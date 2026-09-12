package com.alphatracker.api.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // Find all accounts belonging to a specific user
    List<Account> findAllByUserId(Long userId);

    // Find a specific account and ensure it belongs to the user
    Optional<Account> findByIdAndUserId(Long id, Long userId);

    // Returned as a List rather than a single Account so setPrimaryAccount can
    // self-heal if more than one row was ever left primary=true for a user.
    List<Account> findAllByUserIdAndIsPrimaryTrue(Long userId);
}
