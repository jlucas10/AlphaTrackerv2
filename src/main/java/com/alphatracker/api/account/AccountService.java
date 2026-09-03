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

}
