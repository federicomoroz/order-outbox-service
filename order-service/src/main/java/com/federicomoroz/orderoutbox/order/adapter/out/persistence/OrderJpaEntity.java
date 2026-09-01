package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA row shape for the {@code orders} table. Package-private — nothing outside this package
 * (the mapper and the persistence adapter) should ever touch it directly. */
@Entity
@Table(name = "orders")
class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_amount", nullable = false)
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", nullable = false)
    private String unitPriceCurrency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderJpaEntity() {
        // required by JPA
    }

    OrderJpaEntity(UUID id, UUID customerId, String productId, int quantity, BigDecimal unitPriceAmount,
                   String unitPriceCurrency, String status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPriceAmount = unitPriceAmount;
        this.unitPriceCurrency = unitPriceCurrency;
        this.status = status;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getCustomerId() {
        return customerId;
    }

    String getProductId() {
        return productId;
    }

    int getQuantity() {
        return quantity;
    }

    BigDecimal getUnitPriceAmount() {
        return unitPriceAmount;
    }

    String getUnitPriceCurrency() {
        return unitPriceCurrency;
    }

    String getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
