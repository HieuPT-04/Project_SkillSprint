-- Plan 3C: preserve the calculation inputs for every version checkout.
-- Existing standard sales had no upgrade discount, so their original and final
-- prices are identical and the discount is zero.

ALTER TABLE marketplace_sales
    ADD COLUMN original_gross_coin_amount INTEGER,
    ADD COLUMN discount_coin_amount INTEGER NOT NULL DEFAULT 0;

UPDATE marketplace_sales
SET original_gross_coin_amount = gross_coin_amount
WHERE original_gross_coin_amount IS NULL;

ALTER TABLE marketplace_sales
    ALTER COLUMN original_gross_coin_amount SET NOT NULL;

ALTER TABLE marketplace_sales
    ADD CONSTRAINT ck_marketplace_sales_upgrade_pricing CHECK (
        original_gross_coin_amount >= gross_coin_amount
        AND discount_coin_amount >= 0
        AND original_gross_coin_amount - discount_coin_amount = gross_coin_amount
    );
