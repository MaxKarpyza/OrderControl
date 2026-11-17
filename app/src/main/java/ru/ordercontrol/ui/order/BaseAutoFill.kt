package ru.ordercontrol.ui.order

import ru.ordercontrol.data.db.AddressBaseEntry

/**
 * Подстановка района и количества из базы адресов.
 *
 * Количество заменяется, только если оператор его не вводил: свой выбор он
 * вправе оставить. Район обновляется, пока его не правлен руками.
 *
 * Класс общий для обоих путей ввода заказа — по телефону и вручную, —
 * поэтому подставляется одно и то же.
 */
class BaseAutoFill {

    /** Район, подставленный из базы: пока его не правили вручную, он обновляется. */
    private var autoDistrict: String? = null

    /** Район поправили руками — больше не подставляемый, а свой. */
    fun onDistrictEdited() {
        autoDistrict = null
    }

    /** Новый заказ: прежние подстановки больше не действуют. */
    fun reset() {
        autoDistrict = null
    }

    /**
     * Подставляет район и количество из базы адресов.
     *
     * @param base запись базы адресов по набранному адресу.
     * @param district район, который сейчас в форме.
     * @param bottles количество, которое сейчас в форме.
     * @return район и количество, которые показываем оператору.
     */
    fun apply(
        base: AddressBaseEntry?,
        district: String,
        bottles: String,
    ): Pair<String, String> {
        if (base == null) return district to bottles

        val baseDistrict = base.district
        val newDistrict = if (baseDistrict != null && (district.isBlank() || district == autoDistrict)) {
            autoDistrict = baseDistrict
            baseDistrict
        } else {
            district
        }

        val newBottles = if (bottles.isBlank()) {
            base.bottles?.toString().orEmpty()
        } else {
            bottles
        }

        return newDistrict to newBottles
    }
}
