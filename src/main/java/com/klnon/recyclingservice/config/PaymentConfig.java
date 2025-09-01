package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Arrays;
import java.util.List;

/**
 * 支付系统配置
 */
public class PaymentConfig extends ConfigSection {
    
    public ModConfigSpec.ConfigValue<String> paymentItemType;
    public ModConfigSpec.IntValue crossDimensionAccessCost;
    public ModConfigSpec.ConfigValue<String> insertPaymentMode;
    public ModConfigSpec.ConfigValue<String> extractPaymentMode;
    public ModConfigSpec.ConfigValue<List<? extends String>> dimensionMultipliers;
    
    @Override
    public void build(ModConfigSpec.Builder builder) {
        builder.comment(getDescription()).push(getName());
        
        insertPaymentMode = builder
                .comment("Insert payment mode: all_dimensions_pay, current_dimension_free, all_free")
                .defineInList("insert_payment_mode", "current_dimension_free", 
                        Arrays.asList("all_dimensions_pay", "current_dimension_free", "all_free"));

        paymentItemType = builder
                .comment("Payment item type")
                .define("payment_item_type", "minecraft:emerald");
        
        crossDimensionAccessCost = builder
                .comment("Payment cost for dimension access")
                .defineInRange("cross_dimension_access_cost", 1, 1, 64);
        
        extractPaymentMode = builder
                .comment("Extract payment mode: all_dimensions_pay, current_dimension_free, all_free")
                .defineInList("extract_payment_mode", "current_dimension_free", 
                        Arrays.asList("all_dimensions_pay", "current_dimension_free", "all_free"));
        
        dimensionMultipliers = builder
                .comment("Cost multipliers per dimension. Format: dimension_id:multiplier")
                .defineListAllowEmpty("dimension_multipliers", 
                        List.of("minecraft:overworld:1.0", "minecraft:the_nether:1.0", "minecraft:the_end:2.0"),
                        () -> "minecraft:overworld:1.0",
                        obj -> obj instanceof String && ((String) obj).matches("^[a-z0-9_]+:[a-z0-9_]+:[0-9]+(\\.[0-9]+)?$"));
        
        builder.pop();
    }
    
    @Override
    public String getName() {
        return "payment";
    }
    
    @Override
    public String getDescription() {
        return "Payment system for cross-dimension access";
    }
}