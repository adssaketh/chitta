package com.mansaketh.chitta.ui.manage

import androidx.compose.ui.graphics.vector.ImageVector
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.domain.IconChoice
import com.mansaketh.chitta.core.domain.IconSearch
import com.mansaketh.chitta.core.domain.LegacyCategoryIcons

/** The icons a category can wear, with plain words to search by. Kept in step with CATEGORY_ICONS in tools/gen_icons.py. */
object CategoryIconCatalog {
    val choices: List<IconChoice> = listOf(
        IconChoice("fork-knife", "Food", listOf("restaurant", "meal", "dining", "eat")),
        IconChoice("coffee", "Coffee", listOf("tea", "cafe", "drink")),
        IconChoice("pizza", "Takeaway", listOf("pizza", "delivery", "swiggy", "zomato")),
        IconChoice("cooking-pot", "Cooking", listOf("kitchen", "home food")),
        IconChoice("beer-bottle", "Beer", listOf("bar", "alcohol", "drinks")),
        IconChoice("wine", "Wine", listOf("bar", "alcohol", "dinner")),
        IconChoice("shopping-cart", "Groceries", listOf("supermarket", "vegetables", "store")),
        IconChoice("basket", "Market", listOf("vegetables", "fruit", "shop")),
        IconChoice("shopping-bag", "Shopping", listOf("clothes", "mall", "online")),
        IconChoice("t-shirt", "Clothes", listOf("fashion", "apparel", "shirt")),
        IconChoice("hair-dryer", "Grooming", listOf("salon", "beauty", "haircut")),
        IconChoice("scissors", "Haircut", listOf("barber", "salon")),
        IconChoice("car", "Car", listOf("vehicle", "parking", "service")),
        IconChoice("gas-pump", "Fuel", listOf("petrol", "diesel", "gas")),
        IconChoice("taxi", "Cab", listOf("uber", "ola", "ride", "auto")),
        IconChoice("bus", "Bus", listOf("transport", "metro", "commute")),
        IconChoice("train", "Train", listOf("rail", "irctc", "travel")),
        IconChoice("airplane", "Flights", listOf("travel", "trip", "holiday")),
        IconChoice("bicycle", "Cycle", listOf("bike", "ride")),
        IconChoice("house-line", "Rent", listOf("home", "housing", "landlord")),
        IconChoice("bed", "Stay", listOf("hotel", "lodging", "bed")),
        IconChoice("lightbulb", "Electricity", listOf("power", "utility", "bill")),
        IconChoice("drop", "Water", listOf("utility", "bill", "tanker")),
        IconChoice("wifi-high", "Internet", listOf("broadband", "wifi", "bill")),
        IconChoice("device-mobile", "Phone", listOf("mobile", "recharge", "bill")),
        IconChoice("television", "TV", listOf("dth", "cable", "streaming", "subscription")),
        IconChoice("film-strip", "Movies", listOf("cinema", "theatre", "film")),
        IconChoice("popcorn", "Entertainment", listOf("fun", "outing")),
        IconChoice("music-notes", "Music", listOf("songs", "concert", "subscription")),
        IconChoice("game-controller", "Games", listOf("gaming", "fun", "play")),
        IconChoice("heartbeat", "Health", listOf("doctor", "hospital", "clinic")),
        IconChoice("pill", "Medicine", listOf("pharmacy", "chemist", "tablets")),
        IconChoice("first-aid-kit", "Insurance", listOf("medical", "cover", "premium", "health")),
        IconChoice("barbell", "Fitness", listOf("gym", "sport", "workout")),
        IconChoice("flower-lotus", "Wellness", listOf("yoga", "spa", "temple", "prayer")),
        IconChoice("graduation-cap", "Education", listOf("school", "college", "fees", "course")),
        IconChoice("book", "Books", listOf("reading", "stationery", "study")),
        IconChoice("baby", "Kids", listOf("child", "baby", "toys")),
        IconChoice("paw-print", "Pets", listOf("dog", "cat", "vet")),
        IconChoice("gift", "Gifts", listOf("present", "birthday", "festival", "wedding")),
        IconChoice("umbrella", "Protection", listOf("insurance", "cover", "rain")),
        IconChoice("wrench", "Repairs", listOf("maintenance", "mechanic", "fix")),
        IconChoice("hammer", "Home work", listOf("renovation", "carpenter", "contractor")),
        IconChoice("tree", "Garden", listOf("plants", "outdoors", "park")),
        IconChoice("sun", "Holiday", listOf("vacation", "beach", "trip")),
        IconChoice("money", "Cash", listOf("withdrawal", "atm", "notes")),
        IconChoice("piggy-bank", "Savings", listOf("deposit", "fd", "recurring")),
        IconChoice("hand-coins", "Loan", listOf("emi", "lent", "borrowed", "interest")),
        IconChoice("chart-line-up", "Investments", listOf("mutual fund", "stocks", "sip", "returns")),
        IconChoice("briefcase", "Work", listOf("salary", "office", "business", "income")),
        IconChoice("handshake", "Freelance", listOf("client", "deal", "income", "payment")),
    )

    /** The first dozens by hand, then the ones added so imported categories keep a picture (CategoryIconCatalogMore). */
    val allChoices: List<IconChoice> get() = choices + CategoryIconCatalogMore.choices

    /**
     * The icon a category wears: its own pick ("ph:<name>"), else the stand-in for its old Paddhu picture, else a guess from its name
     * ("Dining" gets the fork and knife). Null only when nothing fits, and the screen then draws the plain tag.
     */
    fun imageFor(iconName: String?, categoryName: String? = null): ImageVector? {
        val key = IconSearch.keyOf(iconName) ?: LegacyCategoryIcons.keyForPicture(iconName) ?: LegacyCategoryIcons.keyForName(categoryName)
        return key?.let { ChittaIcons.byPhosphorName[it] }
    }
}
