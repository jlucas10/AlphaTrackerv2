package com.alphatracker.api.trade;

import java.time.LocalDateTime;

import com.alphatracker.api.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade")
@Data 
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ticker; //Ticker: ES, MES, NQ, MNQ
    
    @Column(nullable = false)
    private String direction; // Long or Short
    
    @Column(nullable = false)
    private Double entryPrice;
    
    @Column(nullable = false)
    private Double exitPrice;
    
    @Column(nullable = false)
    private Integer contracts; // num of lots 
    
    @Column(nullable = false)
    private Double profitLoss; // PNL

    @Column(length = 1000) // Grants extra string length for in depth psychology notes
    private String notes; // e.g., "FVG fill or swept liquidity"

    @Column(nullable = false)
    private LocalDateTime tradeDate;

    // Establishes the One-to-Many mapping. 
    // Tells JPA to link this trade row back to a specific User's primary key
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}