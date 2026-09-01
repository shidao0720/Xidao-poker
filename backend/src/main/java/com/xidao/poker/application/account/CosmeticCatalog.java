package com.xidao.poker.application.account;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 第一版商城使用随构建发布的服务端权威目录。价格、分类和组合内容均不能由客户端提交。
 * 后续若需要运营后台，可在保持 StoreItem 接口不变的前提下迁移到数据库目录。
 */
public final class CosmeticCatalog {
    private static final List<StoreItem> ITEMS = List.of(
            item("observer-frame", "AVATAR_FRAME", "世界线观测者", "WORLDLINE OBSERVER", "STEINS ARCHIVE", "RARE", 320, "◎", "#59cfff",
                    "由无数偏移的世界线刻度构成，头像边缘会泛起微弱的冰蓝脉冲。", List.of("BEST"),
                    List.of("动态圆环头像框", "在线状态呼吸光", "大厅与牌桌同步展示")),
            item("grail-frame", "AVATAR_FRAME", "月海圣杯", "LUNAR GRAIL", "HOLY GRAIL ARCHIVE", "RARE", 680, "♜", "#e0c274",
                    "月光与圣杯纹章交叠的头像框，准备完成时会点亮仪式刻印。", List.of("NEW"),
                    List.of("圣杯纹章头像框", "准备状态点亮效果", "个人主页展示")),
            item("void-frame", "AVATAR_FRAME", "虚空王冠", "CROWN OF VOID", "LOST CHRISTMAS", "EPIC", 1280, "♛", "#d36bff",
                    "晶化王冠悬浮于头像上方，胜利时短暂绽放红紫色虚空结晶。", List.of("FEATURED"),
                    List.of("晶体动态头像框", "胜利触发短动画", "专属资料页光效")),
            item("red-lance", "CARD_BACK", "赤枪斜切", "CRIMSON LANCE", "HOLY GRAIL ARCHIVE", "RARE", 860, "╱", "#e15470",
                    "深蓝牌背被赤色长枪划开，翻牌瞬间留下短促锐利的猩红残光。", List.of("HOT"),
                    List.of("统一牌背", "翻牌赤色斩痕", "其他玩家可见")),
            item("time-machine", "CARD_BACK", "时间机器 C204", "TIME MACHINE C204", "STEINS ARCHIVE", "COMMON", 480, "β", "#5bc9ff",
                    "以时间机器仪表和世界线数值为灵感的终端式牌背。", List.of(),
                    List.of("终端式牌背", "世界线数字微动效", "蓝色翻牌轨迹")),
            item("moon-grail", "CARD_BACK", "月蚀圣杯", "ECLIPSE GRAIL", "HOLY GRAIL ARCHIVE", "EPIC", 1680, "☾", "#e2c270",
                    "当月蚀覆盖圣杯，金色刻印在牌背中央缓慢旋转。", List.of("LIMITED"),
                    List.of("月蚀动态牌背", "金色刻印旋转", "限定边缘粒子")),
            item("chosen-observer", "TITLE", "被选中的观测者", "THE CHOSEN OBSERVER", "STEINS ARCHIVE", "COMMON", 260, "1.048596", "#62d8ff",
                    "向抵达这一条世界线的 Master 致意，称号显示在人名下方。", List.of(),
                    List.of("大厅称号", "牌桌座位称号", "个人主页铭牌")),
            item("king-heroes", "TITLE", "英雄王", "KING OF HEROES", "HOLY GRAIL ARCHIVE", "RARE", 980, "Ⅰ", "#e8c268",
                    "以金色楔形文字构成的稀有称号。", List.of(),
                    List.of("金色动态称号", "轻微辉光", "个人主页铭牌")),
            item("spirit-pulse", "BUTTON_EFFECT", "灵子脉冲", "SPIRITRON PULSE", "ACTION EFFECT", "EPIC", 1380, "✧", "#61e5ff",
                    "点击局内按钮时释放冰蓝灵子，下注粒子连续飞向总底池。", List.of("BEST"),
                    List.of("局内按钮冰蓝爆发", "下注与跟注粒子", "筹码数字联动")),
            item("grail-judgement", "BUTTON_EFFECT", "圣杯裁决", "GRAIL JUDGEMENT", "HOLY GRAIL ARCHIVE", "EPIC", 1880, "✦", "#7cbfff",
                    "加注越接近极限，按钮表面如镜面般逐渐碎裂。", List.of("FEATURED"),
                    List.of("渐进镜裂按钮", "ALL-IN 灵子播报", "加注光线爆发")),
            item("command-spell", "BUTTON_EFFECT", "令咒宣告", "COMMAND SPELL", "HOLY GRAIL ARCHIVE", "RARE", 680, "令", "#ef617a",
                    "行动确认时展开短暂的赤色令咒。", List.of("NEW"),
                    List.of("局内按钮赤色刻印", "令咒光线反馈", "低强度模式")),
            item("worldline-collapse", "VICTORY_EFFECT", "世界线崩解", "WORLDLINE COLLAPSE", "STEINS ARCHIVE", "LEGENDARY", 2600, "∅", "#58dcff",
                    "结算瞬间扫描线失稳，世界线数值崩解后重组为本局收益。", List.of("LIMITED"),
                    List.of("完整胜利结算演出", "底池回流粒子", "专属收益数字动画")),
            item("avalon", "VICTORY_EFFECT", "遥远的理想乡", "AVALON", "HOLY GRAIL ARCHIVE", "LEGENDARY", 3200, "F", "#8acbff",
                    "胜利时展开六层魔术圆，底池化作蓝白光羽回归玩家灵基。", List.of("SEASON", "LIMITED"),
                    List.of("圣杯级胜利演出", "底池回流光羽", "赢家牌型专属铭文")),
            new StoreItem("fate-bundle", "BUNDLE", "Fate stay poker · 始源珍藏", "ORIGIN COLLECTION",
                    "SEASON 00 COLLECTION", "LEGENDARY", 5400, "❖", "#ddbe6f",
                    "收录月海圣杯、赤枪斜切、圣杯裁决与遥远的理想乡。已拥有内容不会重复发放。",
                    List.of("LIMITED", "-18%"),
                    List.of("月海圣杯头像框", "赤枪斜切牌背", "圣杯裁决按钮效果", "遥远的理想乡结算演出"),
                    List.of("grail-frame", "red-lance", "grail-judgement", "avalon"))
    );
    private static final Map<String, StoreItem> BY_KEY;

    static {
        Map<String, StoreItem> items = new LinkedHashMap<>();
        ITEMS.forEach(item -> items.put(item.key(), item));
        BY_KEY = Map.copyOf(items);
    }

    private CosmeticCatalog() {}

    public static List<StoreItem> items() { return ITEMS; }

    public static StoreItem require(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        StoreItem item = BY_KEY.get(key);
        if (item == null) throw new AccountException(AccountErrorCode.COSMETIC_NOT_FOUND, "cosmetic item was not found");
        return item;
    }

    public static CosmeticSlot slot(StoreItem item) {
        if (item.bundle()) throw new AccountException(AccountErrorCode.INVALID_COSMETIC, "bundle cannot be equipped");
        return CosmeticSlot.fromApi(item.category());
    }

    private static StoreItem item(String key, String category, String name, String subtitle, String series,
                                  String rarity, long price, String glyph, String color, String description,
                                  List<String> tags, List<String> features) {
        return new StoreItem(key, category, name, subtitle, series, rarity, price, glyph, color,
                description, tags, features, List.of(key));
    }
}
