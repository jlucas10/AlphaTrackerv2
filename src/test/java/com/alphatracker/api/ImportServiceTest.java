package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountService;
import com.alphatracker.api.account.AccountStatus;
import com.alphatracker.api.account.AccountType;
import com.alphatracker.api.importer.ImportPreviewResponse;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountCheckStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.Row;
import com.alphatracker.api.importer.ImportPreviewResponse.RowStatus;
import com.alphatracker.api.importer.ImportConflictException;
import com.alphatracker.api.importer.ImportService;
import com.alphatracker.api.importer.ImportSource;
import com.alphatracker.api.importer.TopStepXParser;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// The preview must write NOTHING. TradeService is the REAL one (its derivePnl touches no repository), so the
// derived P&L here is the production arithmetic, not a stub.
@ExtendWith(MockitoExtension.class)
public class ImportServiceTest {

    private static final String HEADER = "Id,ContractName,EnteredAt,ExitedAt,EntryPrice,ExitPrice,Fees,PnL,Size,Type,TradeDay,TradeDuration,Commissions";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AccountService accountService;

    private ImportService importService;
    private User alice;
    private Account aliceAccount;
    private Account aliceSecond;

    @BeforeEach
    void setUp() {
        importService = new ImportService(List.of(new TopStepXParser()), accountRepository, tradeRepository,
                new TradeService(tradeRepository, accountRepository), accountService);
        alice = User.builder().id(1L).email("alice@alphatracker.com").password("x").firstName("A").role(Role.USER).build();
        aliceAccount = account(10L, "Topstep 50k A");
        aliceSecond = account(11L, "Topstep 50k B");
    }

    private static Account account(long id, String name) {
        return Account.builder().id(id).name(name).firm("Topstep").accountType(AccountType.EVALUATION)
                .startingBalance(50000.0).currentBalance(50000.0).maxDrawdown(2000.0).build();
    }

    private void owns(Account... accounts) {
        for (Account a : accounts) {
            when(accountRepository.findByIdAndUserId(a.getId(), alice.getId())).thenReturn(Optional.of(a));
        }
    }

    private static InputStream sample() {
        return TopStepXParserTest.class.getResourceAsStream("/imports/topstepx/trades_export.csv");
    }

    private static InputStream csv(String... lines) {
        return new ByteArrayInputStream((String.join("\r\n", lines) + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String row(String id, String contract, String entry, String exit, String pnl, String type) {
        return String.join(",", id, contract, "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", entry, exit,
                "3.6", pnl, "5", type, "10/13/2026 00:00:00 -05:00", "00:05:00", "2.5");
    }

    private void verifyNothingWritten() {
        verify(tradeRepository, never()).save(any());
        verify(tradeRepository, never()).saveAll(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("real sample into one account: 21 NEW rows, derived P&L sums to $495, no warnings, nothing written")
    void realSamplePreview() {
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), eq(ImportSource.TOPSTEPX), anyCollection())).thenReturn(List.of());

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L), sample(), alice);

        assertEquals(21, response.summary().total());
        assertEquals(21, response.summary().ready());
        assertEquals(0, response.summary().duplicates());
        assertEquals(0, response.summary().errors());
        assertEquals(21, response.summary().tradesToCreate());
        assertEquals(495.0, response.rows().stream().mapToDouble(r -> r.trade().profitLoss()).sum(), 1e-9);
        assertTrue(response.rows().stream().allMatch(r -> r.warnings().isEmpty()), "derived P&L matches the file on every row");
        assertEquals("MNQ", response.rows().get(0).trade().ticker());
        assertEquals(-330.0, response.rows().get(0).trade().profitLoss());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("trades already imported into the account are marked DUPLICATE; the rest stay NEW")
    void duplicatesAreMarked() {
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), eq(ImportSource.TOPSTEPX), anyCollection()))
                .thenReturn(List.of("3198364077", "3199300379"));

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L), sample(), alice);

        assertEquals(2, response.summary().duplicates());
        assertEquals(19, response.summary().ready());
        assertEquals(19, response.summary().tradesToCreate());
        assertEquals(RowStatus.DUPLICATE, response.rows().get(0).status());
        assertEquals(RowStatus.DUPLICATE, response.rows().get(1).status());
        assertEquals(RowStatus.NEW, response.rows().get(2).status());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("two accounts: duplicates are judged per account (copy-trading); a row is DUPLICATE only if it is in ALL of them")
    void duplicatesArePerAccount() {
        owns(aliceAccount, aliceSecond);
        // 3198364077 is in BOTH accounts, 3199300379 only in account 10.
        when(tradeRepository.findImportedExternalIds(eq(10L), eq(ImportSource.TOPSTEPX), anyCollection()))
                .thenReturn(List.of("3198364077", "3199300379"));
        when(tradeRepository.findImportedExternalIds(eq(11L), eq(ImportSource.TOPSTEPX), anyCollection()))
                .thenReturn(List.of("3198364077"));

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L, 11L), sample(), alice);

        Row inBoth = response.rows().get(0);
        Row inOne = response.rows().get(1);
        assertEquals(RowStatus.DUPLICATE, inBoth.status());
        assertEquals(RowStatus.NEW, inOne.status()); // still has to be created in account 11
        assertEquals(AccountCheckStatus.DUPLICATE, inOne.accounts().get(0).status());
        assertEquals(AccountCheckStatus.NEW, inOne.accounts().get(1).status());
        assertEquals(1, response.summary().duplicates());
        // 19 rows new in both accounts (x2) + the one row new only in account 11.
        assertEquals(19 * 2 + 1, response.summary().tradesToCreate());
        assertEquals(2, response.accounts().size());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("ownership: another user's account is a SecurityException (403), the file is never read, nothing is looked up")
    void foreignAccountIsForbidden() {
        when(accountRepository.findByIdAndUserId(99L, alice.getId())).thenReturn(Optional.empty());
        InputStream file = mock(InputStream.class);

        assertThrows(SecurityException.class,
                () -> importService.preview(ImportSource.TOPSTEPX, List.of(99L), file, alice));

        verifyNoInteractions(file);
        verify(tradeRepository, never()).findImportedExternalIds(anyLong(), any(), any());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("ownership: one foreign id among owned ones still rejects the whole request")
    void oneForeignAccountAmongOwnedIsForbidden() {
        owns(aliceAccount);
        when(accountRepository.findByIdAndUserId(99L, alice.getId())).thenReturn(Optional.empty());

        assertThrows(SecurityException.class,
                () -> importService.preview(ImportSource.TOPSTEPX, List.of(10L, 99L), sample(), alice));
        verifyNothingWritten();
    }

    @Test
    @DisplayName("no accounts chosen is a 400-style IllegalArgumentException")
    void noAccountsChosen() {
        assertThrows(IllegalArgumentException.class,
                () -> importService.preview(ImportSource.TOPSTEPX, List.of(), sample(), alice));
        assertThrows(IllegalArgumentException.class,
                () -> importService.preview(ImportSource.TOPSTEPX, null, sample(), alice));
    }

    @Test
    @DisplayName("the same account id sent twice is treated once")
    void repeatedAccountIdIsDeduped() {
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), any(), anyCollection())).thenReturn(List.of());

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L, 10L), sample(), alice);

        assertEquals(1, response.accounts().size());
        assertEquals(21, response.summary().tradesToCreate());
    }

    @Test
    @DisplayName("bad rows come back as ERROR rows in file order with the line and message; good rows still preview")
    void errorRowsAreReportedInOrder() {
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), any(), anyCollection())).thenReturn(List.of());

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L), csv(HEADER,
                row("1", "MNQZ6", "100", "101", "10", "Long"),
                row("2", "XYZZ6", "100", "101", "10", "Long"),
                row("3", "MNQZ6", "100", "99", "-10", "Long")), alice);

        assertEquals(List.of(2, 3, 4), response.rows().stream().map(Row::line).toList());
        Row bad = response.rows().get(1);
        assertEquals(RowStatus.ERROR, bad.status());
        assertNull(bad.trade());
        assertTrue(bad.errors().get(0).contains("Unsupported contract 'XYZZ6'"));
        assertEquals(1, response.summary().errors());
        assertEquals(2, response.summary().ready());
        assertEquals(2, response.summary().tradesToCreate());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("a file whose P&L disagrees with the prices gets a warning, and OUR derived figure is the one shown")
    void pnlMismatchWarns() {
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), any(), anyCollection())).thenReturn(List.of());

        // 100 -> 101 on 5 MNQ is +$10.00, but the file claims +$999.
        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L),
                csv(HEADER, row("1", "MNQZ6", "100", "101", "999", "Long")), alice);

        Row only = response.rows().get(0);
        assertEquals(10.0, only.trade().profitLoss());
        assertEquals(999.0, only.trade().sourcePnl());
        assertEquals(1, only.warnings().size());
        assertTrue(only.warnings().get(0).contains("$999.00"), only.warnings().get(0));
        assertTrue(only.warnings().get(0).contains("$10.00"), only.warnings().get(0));
        assertEquals(RowStatus.NEW, only.status(), "a mismatch warns, it does not block");
    }

    @Test
    @DisplayName("a PASSED or FAILED account is flagged in the response but is still importable")
    void closedAccountIsWarnedNotBlocked() {
        aliceAccount.setStatus(AccountStatus.FAILED);
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), any(), anyCollection())).thenReturn(List.of());

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L), sample(), alice);

        assertEquals("FAILED", response.accounts().get(0).status());
        assertTrue(response.accounts().get(0).warning().contains("FAILED"));
        assertEquals(21, response.summary().ready());
    }

    @Test
    @DisplayName("an ACTIVE account has no warning")
    void activeAccountHasNoWarning() {
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), any(), anyCollection())).thenReturn(List.of());

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L), sample(), alice);

        assertNull(response.accounts().get(0).warning());
    }

    @Test
    @DisplayName("an unusable file (missing columns) propagates as IllegalArgumentException and writes nothing")
    void unusableFilePropagates() {
        owns(aliceAccount);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> importService.preview(ImportSource.TOPSTEPX, List.of(10L), csv("Symbol,Qty", "MNQ,1"), alice));

        assertTrue(e.getMessage().contains("TopstepX"));
        verifyNothingWritten();
    }

    @Test
    @DisplayName("a file with only rejected rows skips the duplicate lookup entirely")
    void onlyErrorRowsSkipLookup() {
        owns(aliceAccount);

        ImportPreviewResponse response = importService.preview(ImportSource.TOPSTEPX, List.of(10L),
                csv(HEADER, row("1", "XYZZ6", "100", "101", "10", "Long")), alice);

        assertEquals(1, response.summary().errors());
        verify(tradeRepository, never()).findImportedExternalIds(anyLong(), any(), any(Collection.class));
    }

    @Test
    @DisplayName("confirm: if the unique key refuses a write (a concurrent import won the race) the result is ImportConflictException and no account is saved")
    void concurrentImportBecomesConflict() {
        owns(aliceAccount);
        when(tradeRepository.findImportedExternalIds(eq(10L), any(), anyCollection())).thenReturn(List.of());
        when(tradeRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("uk_trade_account_source_external_id"));

        assertThrows(ImportConflictException.class, () -> importService.confirm(ImportSource.TOPSTEPX, List.of(10L),
                sample(), Map.of(), null, alice));

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm: nothing is read or written when the account is not the caller's")
    void confirmForeignAccountWritesNothing() {
        when(accountRepository.findByIdAndUserId(99L, alice.getId())).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> importService.confirm(ImportSource.TOPSTEPX, List.of(99L),
                sample(), Map.of(), null, alice));

        verifyNothingWritten();
    }
}
