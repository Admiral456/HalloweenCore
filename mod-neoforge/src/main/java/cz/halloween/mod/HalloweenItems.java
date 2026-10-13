package cz.halloween.mod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Equippable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;

import java.util.EnumMap;
import java.util.Map;

public final class HalloweenItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HalloweenCoreMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HalloweenCoreMod.MOD_ID);

    public static final ResourceKey<EquipmentAsset> CRIMSON_WARDEN_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "crimson_warden"));
    public static final ResourceKey<EquipmentAsset> COWL_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "nocturne_cowl"));
    public static final ResourceKey<EquipmentAsset> BATWING_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "batwing_elytra"));

    private static final ArmorMaterial CRIMSON_WARDEN_MATERIAL = new ArmorMaterial(
            80, armorDefense(), 30, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.2F,
            Tags.Items.INGOTS_NETHERITE, CRIMSON_WARDEN_ASSET);

    private static Map<ArmorType, Integer> armorDefense() {
        EnumMap<ArmorType, Integer> values = new EnumMap<>(ArmorType.class);
        values.put(ArmorType.HELMET, 6);
        values.put(ArmorType.CHESTPLATE, 12);
        values.put(ArmorType.LEGGINGS, 9);
        values.put(ArmorType.BOOTS, 6);
        values.put(ArmorType.BODY, 0);
        return values;
    }

    private static final ToolMaterial WARDEN_TOOL_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 5000, 15.0F, 6.0F, 30, Tags.Items.INGOTS_NETHERITE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HALLOWEEN_TAB =
            CREATIVE_TABS.register("halloween", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.halloweencore.halloween"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(CURSED_TALISMAN.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(CURSED_TALISMAN.get());
                        output.accept(NOCTURNE_SOVEREIGN_COWL.get());
                        output.accept(BATWING_ELYTRA.get());
                        output.accept(CRIMSON_WARDEN_HELMET.get());
                        output.accept(CRIMSON_WARDEN_CHESTPLATE.get());
                        output.accept(CRIMSON_WARDEN_LEGGINGS.get());
                        output.accept(CRIMSON_WARDEN_BOOTS.get());
                        output.accept(CRIMSON_WARDEN_SWORD.get());
                        output.accept(CRIMSON_WARDEN_PICKAXE.get());
                        output.accept(CRIMSON_WARDEN_AXE.get());
                        output.accept(CRIMSON_WARDEN_SHOVEL.get());
                        output.accept(CRIMSON_WARDEN_HOE.get());
                        output.accept(CRIMSON_WARDEN_SHIELD.get());
                        output.accept(HALLOWEEN_TOKEN.get());
                        output.accept(CURSED_CANDY.get());
                        output.accept(HAUNTED_MAP.get());
                    }).build());

    public static final DeferredHolder<Item, Item> CURSED_TALISMAN = ITEMS.register("cursed_talisman",
            () -> new CursedTalismanItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredHolder<Item, Item> NOCTURNE_SOVEREIGN_COWL = ITEMS.register("nocturne_sovereign_cowl",
            () -> new Item(new Item.Properties().stacksTo(1).durability(900).enchantable(30)
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                            .setAsset(COWL_ASSET).setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE).build())
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ARMOR,
                                    new AttributeModifier(ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "cowl_armor"),
                                            6.0D, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.HEAD)
                            .build())));

    public static final DeferredHolder<Item, Item> BATWING_ELYTRA = ITEMS.register("batwing_elytra",
            () -> new Item(new Item.Properties().stacksTo(1).durability(768).rarity(Rarity.EPIC)
                    .component(DataComponents.GLIDER, Unit.INSTANCE)
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
                            .setAsset(BATWING_ASSET).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA).build())));

    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_HELMET = ITEMS.register("crimson_warden_helmet",
            () -> new Item(new Item.Properties().humanoidArmor(CRIMSON_WARDEN_MATERIAL, ArmorType.HELMET)
                    .durability(900).enchantable(30)));
    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_CHESTPLATE = ITEMS.register("crimson_warden_chestplate",
            () -> new Item(new Item.Properties().humanoidArmor(CRIMSON_WARDEN_MATERIAL, ArmorType.CHESTPLATE)
                    .durability(1300).enchantable(30)));
    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_LEGGINGS = ITEMS.register("crimson_warden_leggings",
            () -> new Item(new Item.Properties().humanoidArmor(CRIMSON_WARDEN_MATERIAL, ArmorType.LEGGINGS)
                    .durability(1200).enchantable(30)));
    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_BOOTS = ITEMS.register("crimson_warden_boots",
            () -> new Item(new Item.Properties().humanoidArmor(CRIMSON_WARDEN_MATERIAL, ArmorType.BOOTS)
                    .durability(1040).enchantable(30)));

    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_SWORD = ITEMS.register("crimson_warden_sword",
            () -> new Item(new Item.Properties().sword(WARDEN_TOOL_MATERIAL, 6.5F, -2.2F)));
    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_PICKAXE = ITEMS.register("crimson_warden_pickaxe",
            () -> new Item(new Item.Properties().pickaxe(WARDEN_TOOL_MATERIAL, 3.0F, -2.4F)));
    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_AXE = ITEMS.register("crimson_warden_axe",
            () -> new Item(new Item.Properties().axe(WARDEN_TOOL_MATERIAL, 5.0F, -3.0F)));
    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_SHOVEL = ITEMS.register("crimson_warden_shovel",
            () -> new Item(new Item.Properties().shovel(WARDEN_TOOL_MATERIAL, 2.0F, -2.8F)));
    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_HOE = ITEMS.register("crimson_warden_hoe",
            () -> new Item(new Item.Properties().hoe(WARDEN_TOOL_MATERIAL, 2.0F, -2.5F)));

    public static final DeferredHolder<Item, Item> CRIMSON_WARDEN_SHIELD = ITEMS.register("crimson_warden_shield",
            () -> new Item(new Item.Properties().durability(1800).stacksTo(1)
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.OFFHAND)
                            .setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC).build())
                    .component(DataComponents.BLOCKS_ATTACKS,
                            Items.SHIELD.components().get(DataComponents.BLOCKS_ATTACKS))));

    public static final DeferredHolder<Item, Item> HALLOWEEN_TOKEN = ITEMS.registerSimpleItem("halloween_token",
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredHolder<Item, Item> CURSED_CANDY = ITEMS.registerSimpleItem("cursed_candy",
            new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder()
                    .nutrition(2).saturationModifier(0.4F).alwaysEdible().build()).rarity(Rarity.UNCOMMON));
    public static final DeferredHolder<Item, Item> HAUNTED_MAP = ITEMS.registerSimpleItem("haunted_map",
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    private HalloweenItems() {}
}
