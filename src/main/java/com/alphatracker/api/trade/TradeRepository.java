package com.alphatracker.api.trade;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long>{
    // Spring Data JPA will automatically analyze 
    // this method name and generate the SQL query behind the scenes:
    // "SELECT * FROM trade WHERE user_id = ?"
    List<Trade> findByUserId(Long userId);
}
