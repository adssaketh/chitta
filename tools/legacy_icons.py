#!/usr/bin/env python3
"""Maps the old Paddhu (Cashew) category picture names to Phosphor icons and writes the Kotlin that uses the map.

    python3 tools/legacy_icons.py package/assets/regular     (package/ from `npm pack @phosphor-icons/core@2.1.1`)

Writes LegacyCategoryIcons.kt (:core:domain) and CategoryIconCatalogMore.kt (app). Then run tools/gen_icons.py so ChittaIcons has the new icons.
Edit MAP to change a mapping, rerun both, commit the results. Every target must be a Phosphor file name (the script checks).
"""
import os, re, sys

# old picture name (no .png, no "(1)") -> Phosphor name
MAP = {
 "3d-printer":"printer","air-hockey":"game-controller","anchor":"anchor","antivirus":"shield-check","apple":"orange-slice",
 "atm-machine":"hand-withdraw","back-undo-arrow":"arrow-counter-clockwise","ball":"soccer-ball","balloons":"balloon","bank":"bank",
 "barber":"scissors","baseball-player":"baseball","battery-charge":"battery-charging","beach-umbrella":"umbrella-simple","bicycle":"bicycle",
 "bill":"receipt","bills":"receipt","binoculars":"binoculars","birdhouse":"bird","bookshelf":"books","bottles":"beer-bottle",
 "bouquet":"flower-tulip","bowling":"bowling-ball","box":"package","bread":"bread","briefcase":"briefcase","bubble-tea":"pint-glass",
 "butterfly":"butterfly","cabin":"tent","cactus":"cactus","cake":"cake","calculator":"calculator","calendar":"calendar-blank",
 "camera":"camera","campfire":"campfire","candy":"cookie","cap":"baseball-cap","car":"car","car-charging-station":"charging-station",
 "car-key":"key","cards":"cards","cash-bill-dollar":"money","cat":"cat","celebration":"confetti","charts":"chart-bar",
 "checker-chess-board":"checkerboard","chef-hat":"chef-hat","chess":"crown-simple","christmas-tree":"tree-evergreen","church":"church",
 "cleaning":"broom","clipboard":"clipboard-text","clock":"clock","clothes-hanger":"coat-hanger","cloudy":"cloud","coconut-tree":"tree-palm",
 "code":"code","coffee-cup":"coffee","coffee":"coffee","coin":"coin","color-palette":"palette","compass":"compass","confetti":"confetti",
 "construction-helmet":"hard-hat","cookies":"cookie","cottage":"house","cowboy-hat":"smiley","credit-card":"credit-card",
 "cricket-bat":"cricket","crypto":"currency-btc","cupcake":"cake","curry":"cooking-pot","cutlery":"fork-knife","decrease":"trend-down",
 "delivery-truck":"truck","dental-care":"tooth","desktop-computer":"desktop","diamond":"diamond","dice":"dice-five","dog":"dog",
 "dollar-coin":"coin","donut":"cookie","double-bed":"bed","dress":"dress","dvd":"disc","earth":"globe-hemisphere-east","eggs":"egg",
 "emergency-exit":"sign-out","envelope":"envelope-simple","essay":"article","exchange-arrows-circle":"arrows-left-right",
 "exchange-arrows":"arrows-left-right","extinguisher":"fire-extinguisher","face-mask":"first-aid","fast-food":"hamburger",
 "favourite":"heart","feathers":"feather","feeding-bottle":"baby","fireplace":"fire","fish":"fish","fishing-rod":"fish-simple",
 "fizzy-drink":"jar","flashlight":"flashlight","flat-tire":"tire","flower":"flower","folder":"folder","food-tray":"hamburger",
 "fried-egg":"egg","fried-potatoes":"hamburger","fruit":"orange-slice","fuel":"gas-pump","furniture":"armchair","gamepad":"game-controller",
 "garden-hose":"plant","gas-station":"gas-pump","gas-valve":"flame","gasoline":"gas-can","gears":"gear-six","gift-card":"gift","gift":"gift",
 "glass-of-water":"drop","glass":"wine","golf-ball":"golf","golf":"golf","graduation":"graduation-cap","grill":"cooking-pot",
 "groceries":"shopping-cart","guitar":"guitar","haircut":"scissors","hamster":"paw-print","hand-fan":"fan","headphones":"headphones",
 "healthcare-and-medical":"first-aid-kit","healthy-food":"carrot","hearing-aid":"ear","heart":"heart","helicopter":"airplane",
 "high-heels":"high-heel","hiking-backpack":"backpack","hockey-stick":"hockey","home2":"house","house":"house","hoodie":"t-shirt",
 "ice-cream-cup":"ice-cream","idea":"lightbulb","image":"image","increase":"trend-up","internet-globe":"globe","investment":"chart-line-up",
 "key":"key","keyboard":"keyboard","kite":"paper-plane-tilt","laptop":"laptop","lighthouse":"lighthouse","lightning-bolt":"lightning",
 "limousine":"car-profile","loan":"hand-coins","location-pin":"map-pin","locomotive":"train","magic-box":"magic-wand",
 "magnifying-glass":"magnifying-glass","make-up":"sparkle","makeup":"sparkle","map":"map-trifold","media-content":"play-circle",
 "microphone":"microphone","milk":"cow","mixer":"cooking-pot","money-bag":"money","money-bag2":"money","money":"money","motor-bike":"motorcycle",
 "multivitamin":"pill","music":"music-notes","necklace":"diamond","noodles":"bowl-food","note":"note","oldschool-telephone":"phone",
 "open-book":"book-open","open-email":"envelope-open","orange-juice":"pint-glass","organic-food":"leaf","package":"package","padlock":"lock",
 "paper-bill":"receipt","paper-ticket":"ticket","parents":"users","parking":"letter-circle-p","pen":"pen","pencil":"pencil",
 "pet-bowl":"bowl-food","piano":"piano-keys","picnic":"basket","piggy-bank":"piggy-bank","pizza":"pizza","plane":"airplane","plant":"plant",
 "popcorn":"popcorn","popsicle":"popsicle","portable-game-console":"game-controller","price-tag":"tag","pumpkin":"ghost","radio":"radio",
 "rain":"cloud-rain","rainbow":"rainbow","raw-meat":"fork-knife","recycling":"recycle","reload":"arrows-clockwise","rent":"house-line",
 "robot":"robot","safe-box":"vault","safety-helmet":"hard-hat","salad":"leaf","sandals":"sneaker","sandwich":"hamburger","sauces":"jar",
 "school-backpack":"backpack","school-bus":"bus","science":"flask","security-box":"vault","settings":"gear","shopping-cart":"shopping-cart",
 "shopping":"shopping-bag","shrimp":"fish","sim-card":"sim-card","skincare":"sparkle","smartphone":"device-mobile","sneakers":"sneaker",
 "snooker":"number-circle-eight","snorkling":"waves","snowman":"snowflake","socket":"plug","spaghetti":"bowl-food","speaker":"speaker-high",
 "sports":"trophy","sticky-notes":"note","subscription":"repeat","sushi":"fish","tablet":"device-tablet","taco":"pepper","taxi":"taxi",
 "tea":"coffee","teddy-bear":"smiley","telescope":"binoculars","television":"television","theatre":"mask-happy","tickets":"ticket",
 "tie":"user-circle","toiletries":"toilet-paper","top-hat":"crown-simple","tram":"tram","trash":"trash","tree":"tree","trophy":"trophy",
 "tshirt":"t-shirt","umbrella":"umbrella","user":"user","vision":"eyeglasses","wallet":"wallet","washing-machine":"washing-machine","watch":"watch",
 "water-bottle":"drop-half-bottom","water-tap":"drop","watering-can":"plant","weight":"barbell","wifi":"wifi-high","winter-hat":"snowflake",
 "yarn-ball":"circle-dashed","target":"target","delivery":"truck",
}
# category NAME words -> Phosphor name, for pictures nobody mapped. First match wins, so keep the specific ones first.
BY_NAME = [
 (("grocer","vegetable","supermarket"),"shopping-cart"),(("dining","food","restaurant","eating","lunch","dinner","meal","snack"),"fork-knife"),
 (("coffee","tea","cafe"),"coffee"),(("fuel","petrol","diesel"),"gas-pump"),(("cab","uber","ola","taxi"),"taxi"),
 (("transit","transport","commute","metro","bus"),"bus"),(("train","rail"),"train"),(("flight","air","travel","trip"),"airplane"),
 (("rent","housing","home"),"house-line"),(("electric","power"),"lightbulb"),(("water",),"drop"),(("internet","wifi","broadband"),"wifi-high"),
 (("phone","mobile","recharge"),"device-mobile"),(("entertain","movie","cinema"),"film-strip"),(("music",),"music-notes"),
 (("game",),"game-controller"),(("health","doctor","medical","hospital"),"heartbeat"),(("medicine","pharmacy"),"pill"),
 (("insurance",),"first-aid-kit"),(("gym","fitness","sport"),"barbell"),(("school","education","course","tuition"),"graduation-cap"),
 (("book",),"book"),(("kid","child","baby"),"baby"),(("pet",),"paw-print"),(("gift","present"),"gift"),(("cloth","apparel","fashion"),"t-shirt"),
 (("shop","mall","online"),"shopping-bag"),(("beauty","salon","grooming","care"),"hair-dryer"),(("repair","maintenance","service"),"wrench"),
 (("bill","fee","tax","utility"),"receipt"),(("subscription",),"repeat"),(("salary","income","work","office","business"),"briefcase"),
 (("saving","deposit"),"piggy-bank"),(("invest","stock","fund","sip"),"chart-line-up"),(("loan","emi","debt","borrow","lent"),"hand-coins"),
 (("cash","atm","withdraw"),"money"),(("transfer",),"arrows-left-right"),(("donat","charity","temple","church"),"hand-heart"),
]

def pascal(k): return "".join(p.capitalize() for p in k.split("-"))
def label(k): return " ".join(p.capitalize() for p in k.split("-"))

def main(src):
    have = set(f[:-4] for f in os.listdir(src) if f.endswith(".svg"))
    bad = sorted({v for v in list(MAP.values()) + [v for _, v in BY_NAME] if v not in have})
    if bad: sys.exit("not in Phosphor 2.1.1: " + ", ".join(bad))
    # icons already in the catalog (read from gen_icons.py) are not added twice
    g = open(os.path.join(os.path.dirname(__file__), "gen_icons.py")).read()
    existing = set(re.findall(r'\("Cat\w+", "([a-z0-9-]+)"\)', g.split("# EXTRA")[0]))
    wanted = sorted({v for v in MAP.values() if v not in existing} | {v for _, v in BY_NAME if v not in existing})
    # words to search by: the old names that point here
    words = {}
    for old, new in MAP.items(): words.setdefault(new, []).append(old.replace("-", " "))
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    map_lines = "\n".join(f'        "{k}" to "{v}",' for k, v in sorted(MAP.items()))
    name_lines = "\n".join(f'        listOf({", ".join(chr(34)+w+chr(34) for w in ws)}) to "{v}",' for ws, v in BY_NAME)
    open(os.path.join(root, "core/domain/src/main/kotlin/com/mansaketh/chitta/core/domain/LegacyCategoryIcons.kt"), "w").write(f'''package com.mansaketh.chitta.core.domain

/*
 * Generated by tools/legacy_icons.py; do not edit by hand.
 * The old Paddhu (Cashew) app drew categories with picture files such as "cutlery.png". Chitta draws Phosphor line icons, so an imported
 * category keeps its old picture name and this table says which Phosphor icon stands in for it (DECISIONS D188).
 */
object LegacyCategoryIcons {{
    private val picture = mapOf(
{map_lines}
    )

    private val byName: List<Pair<List<String>, String>> = listOf(
{name_lines}
    )

    /** "assets/categories/Car(2).PNG" -> "car". */
    fun normalise(iconName: String): String =
        iconName.substringAfterLast('/').lowercase().removeSuffix(".png").replace(Regex("\\\\(\\\\d+\\\\)$"), "").trim()

    /** The Phosphor key for an old picture name, or null when it is unknown. */
    fun keyForPicture(iconName: String?): String? = iconName?.takeIf {{ it.isNotBlank() && !it.startsWith(IconSearch.PREFIX) }}?.let {{ picture[normalise(it)] }}

    /** A guess from the category's own name ("Dining" -> fork and knife), or null. Only used when the picture is unknown or missing. */
    fun keyForName(categoryName: String?): String? {{
        val n = categoryName?.lowercase()?.takeIf {{ it.isNotBlank() }} ?: return null
        val tokens = n.split(Regex("[^a-z]+")).filter {{ it.isNotEmpty() }}
        // A short keyword must be the whole word ("air" is not "hair"); a longer one may start the word ("entertain" is "entertainment").
        fun hit(w: String, k: String) = w == k || w == k + "s" || (k.length > 3 && w.startsWith(k))
        return byName.firstOrNull {{ (words, _) -> words.any {{ k -> tokens.any {{ w -> hit(w, k) }} }} }}?.second
    }}

    /** Every Phosphor key this table can return (for the test that each one has a drawn icon). */
    val allKeys: Set<String> = picture.values.toSet() + byName.map {{ it.second }}

    val knownPictures: Set<String> get() = picture.keys
}}
''')
    choice_lines = "\n".join(f'        IconChoice("{k}", "{label(k)}", listOf({", ".join(chr(34)+w+chr(34) for w in sorted(set(words.get(k, [])))[:6])})),' for k in wanted)
    open(os.path.join(root, "app/src/main/java/com/mansaketh/chitta/ui/manage/CategoryIconCatalogMore.kt"), "w").write(f'''package com.mansaketh.chitta.ui.manage

import com.mansaketh.chitta.core.domain.IconChoice

/** Generated by tools/legacy_icons.py; do not edit by hand. The icons added so imported categories keep a picture; the search words are the old picture names. */
object CategoryIconCatalogMore {{
    val choices: List<IconChoice> = listOf(
{choice_lines}
    )
}}
''')
    # gen_icons.py extra list
    block = "# EXTRA (written by tools/legacy_icons.py)\nEXTRA = [\n" + "\n".join(f'    ("Cat{pascal(k)}", "{k}"),' for k in wanted) + "\n]\n"
    g = re.sub(r"# EXTRA.*?\n\]\n", "", g, flags=re.S)
    g = re.sub(r"ICONS \+= CATEGORY_ICONS( \+ EXTRA)?\n", lambda m: block + "ICONS += CATEGORY_ICONS + EXTRA\n", g)
    open(os.path.join(root, "tools/gen_icons.py"), "w").write(g)
    print(len(MAP), "pictures mapped;", len(wanted), "new icons")

if __name__ == "__main__":
    main(sys.argv[1])
