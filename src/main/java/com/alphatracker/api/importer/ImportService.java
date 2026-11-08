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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountCheck;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountCheckStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountInfo;
import com.alphatracker.api.importer.ImportPreviewResponse.Row;
import com.alphatracker.api.importer.ImportPreviewResponse.RowStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.Summary;
import com.alphatracker.api.importer.ImportPreviewResponse.TradePreview;
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

    // Spring hands over every TradeImportParser bean, so a new source is picked up with no change here.
    public ImportService(List<TradeImportParser> parserBeans, AccountRepository accountRepository,
            TradeRepository tradeRepository, TradeService tradeService) {
        for (TradeImportParser parser : parserBeans) {
            parsers.put(parser.source(), parser);
        }
        this.accountRepository = accountRepository;
        this.tradeRepository = tradeRepository;
        this.tradeService = tradeService;
    }

    // Writes NOTHING (readOnly transaction, and no save call exists on this path).
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(ImportSource source, List<Long> accountIds, InputStream file, User user) {
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

        List<Row> rows = new ArrayList<>();
        for (ImportedTrade trade : parsed.trades()) {
            rows.add(toRow(trade, accounts, alreadyImported));
        }
        for (ImportRowError error : parsed.errors()) {
            rows.add(new Row(error.line(), error.externalId(), RowStatus.ERROR, null, List.of(), List.of(),
                    List.of(error.message())));
        }
        rows.sort(Comparator.comparingInt(Row::line));

        return new ImportPreviewResponse(source, accounts.stream().map(ImportService::toInfo).toList(),
                summarize(rows), rows);
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
