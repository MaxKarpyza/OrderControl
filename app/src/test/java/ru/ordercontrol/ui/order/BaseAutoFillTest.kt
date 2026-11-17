package ru.ordercontrol.ui.order

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ordercontrol.data.db.AddressBaseEntry

class BaseAutoFillTest {

    private fun base(district: String? = "Гладковка", bottles: Int? = 2) = AddressBaseEntry(
        key = "артема 114",
        address = "Артема 114",
        district = district,
        bottles = bottles,
        updatedAt = 0L,
    )

    @Test
    fun `пустая форма получает район и количество из базы`() {
        val fill = BaseAutoFill()

        val (district, bottles) = fill.apply(base(), district = "", bottles = "")

        assertEquals("Гладковка", district)
        assertEquals("2", bottles)
    }

    @Test
    fun `количество оператора не перебивается`() {
        val fill = BaseAutoFill()

        val (_, bottles) = fill.apply(base(), district = "", bottles = "5")

        assertEquals("5", bottles)
    }

    @Test
    fun `район оператора не перебивается`() {
        val fill = BaseAutoFill()

        val (district, _) = fill.apply(base(), district = "Солнечный", bottles = "")

        assertEquals("Солнечный", district)
    }

    @Test
    fun `подставленный район обновляется новым адресом`() {
        val fill = BaseAutoFill()
        fill.apply(base(district = "Гладковка"), district = "", bottles = "")

        val (district, _) = fill.apply(
            base(district = "Вишневского"),
            district = "Гладковка",
            bottles = "",
        )

        assertEquals("Вишневского", district)
    }

    @Test
    fun `правленый руками район не обновляется`() {
        val fill = BaseAutoFill()
        fill.apply(base(district = "Гладковка"), district = "", bottles = "")
        fill.onDistrictEdited()

        val (district, _) = fill.apply(
            base(district = "Вишневского"),
            district = "Солнечный",
            bottles = "",
        )

        assertEquals("Солнечный", district)
    }

    @Test
    fun `в базе нет района и количества — форма не меняется`() {
        val fill = BaseAutoFill()

        val (district, bottles) = fill.apply(
            base(district = null, bottles = null),
            district = "",
            bottles = "",
        )

        assertEquals("", district)
        assertEquals("", bottles)
    }
}
