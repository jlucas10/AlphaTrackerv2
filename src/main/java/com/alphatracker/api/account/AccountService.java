package com.alphatracker.api.account;

import org.springframework.stereotype.Service;
import com.alphatracker.api.user.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
// validates business logic, assigns the authenticated user, 
// and converts entities to DTO responses.

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(User user) {
        return accountRepository.findAllByUserId(user.getId())
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request, User user) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Account name is required.");
        }
        if (request.getFirm() == null || request.getFirm().trim().isEmpty()) {
            throw new IllegalArgumentException("Prop firm name is required.");
        }
        if (request.getStartingBalance() == null || request.getStartingBalance() <= 0) {
            throw new IllegalArgumentException("Starting balance must be greater than 0.");
        }
        if (request.getMaxDrawdown() == null || request.getMaxDrawdown() <= 0) {
            throw new IllegalArgumentException("Max drawdown limit must be greater than 0.");
        }
        if (request.getAccountType() == null) {
            request.setAccountType(AccountType.EVALUATION);
        }

        Account account = Account.builder()
                .name(request.getName().trim())
                .firm(request.getFirm().trim())
                .accountType(request.getAccountType())
                .startingBalance(request.getStartingBalance())
                .currentBalance(request.getStartingBalance())
                .profitTarget(request.getProfitTarget())
                .maxDrawdown(request.getMaxDrawdown())
                .active(true)
                .user(user)
                .build();

        Account saved = accountRepository.save(account);
        return AccountResponse.fromEntity(saved);
    }
}
