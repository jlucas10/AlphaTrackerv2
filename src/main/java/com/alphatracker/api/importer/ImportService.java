package com.alphatracker.api.importer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountProgress;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountService;
import com.alphatracker.api.account.DrawdownSnapshot;
import com.alphatracker.api.account.AccountStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountCheck;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountCheckStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountInfo;
import com.alphatracker.api.importer.ImportPreviewResponse.Row;
import com.alphatracker.api.importer.ImportPreviewResponse.RowStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.Summary;
import com.alphatracker.api.importer.ImportPreviewResponse.TradePreview;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.user.User;

// Orchestrates an import. Step 2 is the read-only half: parse, validate, work out what would happen.
// It owns no format knowledge (that is the parser's) and no money math (that is TradeService.derivePnl).
@Service
public class ImportService {

    // P&L differences smaller than a cent are floating-point noise, not a real mismatch.
    private static final double PNL_TOLERANCE = 0.005;

    private final Map<ImportSource, TradeImportParser> parsers = new EnumMap<>(ImportSource.class);
    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final TradeService tradeService;
    private final AccountService accountService;

    // Spring hands over every TradeImportParser bean, so a new source is picked up with no change here.
    public ImportService(List<TradeImportParser> parserBeans, AccountRepository accountRepository,
            TradeRepository tradeRepository, TradeService tradeService, AccountService accountService) {
        for (TradeImportParser parser : parserBeans) {
            parsers.put(parser.source(), parser);
        }
        this.accountRepository = accountRepository;
        this.tradeRepository = tradeRepository;
        this.tradeService = tradeService;
        this.accountService = accountService;
    }

    // What both steps need: the parser, the owned accounts (ownership BEFORE the file is read), the parsed
    // file, and which broker ids each account already has. Preview and confirm share this on purpose, so what
    // the trader was shown is exactly what confirm acts on.
    private record Plan(List<Account> accounts, ParsedImport parsed, Map<Long, Set<String>> alreadyImported) {
    }

    private Plan plan(ImportSource source, List<Long> accountIds, InputStream file, User user) {
        TradeImportParser parser = parsers.get(source);
        if (parser == null) {
            throw new IllegalArgumentException("Importing from " + source + " is not available yet.");
        }
        // Ownership is settled BEFORE the file is read: a trader pointing at someone else's account learns
        // nothing about the file, and gets the same 403 whether the account is theirs-to-be or just missing.
        List<Account> accounts = resolveOwnedAccounts(accountIds, user);

        ParsedImport parsed = parser.parse(file);

        Set<String> externalIds = parsed.trades().stream().map(ImportedTrade::externalId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Set<String>> alreadyImported = new java.util.HashMap<>();
        for (Account account : accounts) {
            alreadyImported.put(account.getId(), externalIds.isEmpty()
                    ? Set.of()
                    : new HashSet<>(tradeRepository.findImportedExternalIds(account.getId(), source, externalIds)));
        }
        return new Plan(accounts, parsed, alreadyImported);
    }

    // Writes NOTHING (readOnly transaction, and no save call exists on this path).
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(ImportSource source, List<Long> accountIds, InputStream file, User user) {
        Plan plan = plan(source, accountIds, file, user);

        List<Row> rows = new ArrayList<>();
        for (ImportedTrade trade : plan.parsed().trades()) {
            rows.add(toRow(trade, plan.accounts(), plan.alreadyImported()));
        }
        for (ImportRowError error : plan.parsed().errors()) {
            rows.add(new Row(error.line(), error.externalId(), RowStatus.ERROR, null, List.of(), List.of(),
                    List.of(error.message())));
        }
        rows.sort(Comparator.comparingInt(Row::line));

        return new ImportPreviewResponse(source, plan.accounts().stream().map(ImportService::toInfo).toList(),
                summarize(rows), rows);
    }

    // Writes the new trades in ONE transaction: either every trade for every chosen account lands, or (on any
    // failure) none do. Rejected rows are not written but do not stop the valid ones; they are reported.
    //
    // The file is parsed and checked again here instead of trusting a preview: the client could send a
    // different file, or another request could have imported the same trades since the preview.
    //
    // followedPlanByExternalId says, per broker trade id, whether the trader followed their plan. A trade not
    // listed gets `defaultFollowedPlan`, and if that is null too the trade stays UNSPECIFIED (null) - an
    // import never claims "followed" on the trader's behalf (hand-logging defaults to true; this must not).
    @Transactional
    public ImportConfirmResponse confirm(ImportSource source, List<Long> accountIds, InputStream file,
            Map<String, Boolean> followedPlanByExternalId, Boolean defaultFollowedPlan, User user) {
        Plan plan = plan(source, accountIds, file, user);

        List<Trade> toSave = new ArrayList<>();
        Map<Long, int[]> counts = new java.util.HashMap<>(); // accountId -> {created, skipped}
        for (Account account : plan.accounts()) {
            counts.put(account.getId(), new int[2]);
        }

        for (Account account : plan.accounts()) {
            Set<String> have = plan.alreadyImported().get(account.getId());
            for (ImportedTrade imported : plan.parsed().trades()) {
                if (have.contains(imported.externalId())) {
                    counts.get(account.getId())[1]++;
                    continue;
                }
                Boolean followed = followedPlanByExternalId.getOrDefault(imported.externalId(), defaultFollowedPlan);
                double pnl = tradeService.derivePnl(imported.instrument(), imported.direction(),
                        imported.entryPrice(), imported.exitPrice(), imported.contracts());
                toSave.add(Trade.builder()
                        .ticker(imported.instrument().name())
                        .direction(imported.direction())
                        .entryPrice(imported.entryPrice())
                        .exitPrice(imported.exitPrice())
                        .contracts(imported.contracts())
                        .profitLoss(pnl)
                        .followedPlan(followed)
                        .tradeDate(imported.entryTime())
                        .user(user)
                        .account(account)
                        .source(source)
                        .externalId(imported.externalId())
                        .build());
                tradeService.applyPnlToBalance(account, pnl);
                counts.get(account.getId())[0]++;
            }
        }

        try {
            tradeRepository.saveAll(toSave);
            // One save per account, after the whole batch, instead of one per trade.
            for (Account account : plan.accounts()) {
                accountRepository.save(account);
            }
        } catch (DataIntegrityViolationException e) {
            // The unique key caught a concurrent import of the same trades. The exception rolls everything back.
            throw new ImportConflictException(
                    "Some of these trades were imported by another request just now. Nothing was saved; preview again.");
        }

        List<ImportConfirmResponse.AccountResult> results = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        for (Account account : plan.accounts()) {
            int[] c = counts.get(account.getId());
            created += c[0];
            skipped += c[1];
            // Read back through the same replay the dashboard uses, AFTER the write, so the response shows
            // the real post-import state (including trades dated before ones already on the account).
            DrawdownSnapshot drawdown = accountService.computeDrawdownSnapshot(account);
            AccountProgress progress = accountService.computeProgress(account);
            results.add(new ImportConfirmResponse.AccountResult(account.getId(), account.getName(), c[0], c[1],
                    account.getCurrentBalance(), drawdown.highWaterMark(), drawdown.drawdownFloor(),
                    progress.profit(), progress.suggestedStatus(), progress.breachedOn(),
                    progress.targetReachedOn()));
        }
        return new ImportConfirmResponse(source, created, skipped, plan.parsed().errors().size(), results,
                plan.parsed().errors());
    }

    // Every id must be an account this user owns. Missing and someone-else's are deliberately
    // indistinguishable (both 403), so account ids cannot be probed.
    private List<Account> resolveOwnedAccounts(List<Long> accountIds, User user) {
        if (accountIds == null || accountIds.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one account to import into.");
        }
        List<Account> accounts = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(accountIds)) {
            accounts.add(accountRepository.findByIdAndUserId(id, user.getId())
                    .orElseThrow(() -> new SecurityException("Account not found or does not belong to user.")));
        }
        return accounts;
    }

    private Row toRow(ImportedTrade trade, List<Account> accounts, Map<Long, Set<String>> alreadyImported) {
        double derived = tradeService.derivePnl(trade.instrument(), trade.direction(), trade.entryPrice(),
                trade.exitPrice(), trade.contracts());

        List<String> warnings = new ArrayList<>();
        if (trade.sourcePnl() != null && Math.abs(trade.sourcePnl() - derived) > PNL_TOLERANCE) {
            warnings.add(String.format(Locale.ROOT,
                    "The file reports P&L of $%.2f but AlphaTracker calculates $%.2f from the prices. "
                            + "AlphaTracker's figure will be used.",
                    trade.sourcePnl(), derived));
        }

        List<AccountCheck> checks = accounts.stream()
                .map(account -> new AccountCheck(account.getId(),
                        alreadyImported.get(account.getId()).contains(trade.externalId())
                                ? AccountCheckStatus.DUPLICATE
                                : AccountCheckStatus.NEW))
                .toList();
        boolean allDuplicate = checks.stream().allMatch(check -> check.status() == AccountCheckStatus.DUPLICATE);

        TradePreview preview = new TradePreview(trade.instrument().name(), trade.direction(), trade.entryPrice(),
                trade.exitPrice(), trade.contracts(), trade.entryTime(), trade.exitTime(), derived,
                trade.sourcePnl());
        return new Row(trade.line(), trade.externalId(), allDuplicate ? RowStatus.DUPLICATE : RowStatus.NEW,
                preview, checks, warnings, List.of());
    }

    private static AccountInfo toInfo(Account account) {
        AccountStatus status = account.currentStatus();
        String warning = status == AccountStatus.ACTIVE ? null
                : "This account is marked " + status + ". Imported trades are still saved to it.";
        return new AccountInfo(account.getId(), account.getName(), status.name(), warning);
    }

    private static Summary summarize(Collection<Row> rows) {
        int ready = 0;
        int duplicates = 0;
        int errors = 0;
        int toCreate = 0;
        for (Row row : rows) {
            switch (row.status()) {
                case NEW -> ready++;
                case DUPLICATE -> duplicates++;
                case ERROR -> errors++;
            }
            toCreate += (int) row.accounts().stream().filter(c -> c.status() == AccountCheckStatus.NEW).count();
        }
        return new Summary(rows.size(), ready, duplicates, errors, toCreate);
    }
}
