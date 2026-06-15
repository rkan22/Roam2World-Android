package im.angry.openeuicc.ui

object ShoppingCartStore {
    data class Customer(
        val id: String,
        var name: String,
        var subtitle: String = "",
        var email: String = "",
        var phone: String = "",
        var company: String = "",
        var country: String = "",
        var notes: String = ""
    )

    data class Item(
        val id: String,
        val title: String,
        val subtitle: String,
        val provider: String,
        val price: String,
        var quantity: Int = 1,
        var customerId: String? = null,
        var customerName: String? = null
    )

    private val customers = mutableListOf(
        Customer(
            id = "customer_1",
            name = "Customer 1",
            subtitle = "Add customer info",
            company = "",
            email = "",
            phone = ""
        ),
        Customer(
            id = "customer_2",
            name = "Customer 2",
            subtitle = "Add customer info",
            company = "",
            email = "",
            phone = ""
        )
    )

    private val items = mutableListOf<Item>()

    fun customers(): List<Customer> = customers.toList()

    fun all(): List<Item> = items.toList()

    fun count(): Int = items.sumOf { it.quantity }

    fun isEmpty(): Boolean = items.isEmpty()

    fun addCustomer(name: String) {
        val clean = name.trim().ifBlank { "Customer ${customers.size + 1}" }
        val id = "customer_${customers.size + 1}"
        customers += Customer(id, clean, "Add customer info")
    }

    fun updateCustomer(
        id: String,
        name: String,
        email: String,
        phone: String,
        company: String,
        country: String,
        notes: String
    ) {
        val customer = customers.firstOrNull { it.id == id } ?: return
        customer.name = name.trim().ifBlank { customer.name }
        customer.email = email.trim()
        customer.phone = phone.trim()
        customer.company = company.trim()
        customer.country = country.trim()
        customer.notes = notes.trim()
        customer.subtitle = listOf(customer.company, customer.email, customer.phone)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "Add customer info" }

        items.filter { it.customerId == id }.forEach {
            it.customerName = customer.name
        }
    }

    fun add(item: Item) {
        val normalized = item.copy(quantity = item.quantity.coerceAtLeast(1))
        val existing = items.firstOrNull {
            it.id == normalized.id && it.customerId == normalized.customerId
        }

        if (existing != null) {
            existing.quantity += normalized.quantity
        } else {
            items += normalized
        }
    }

    fun assignCustomer(itemId: String, customer: Customer?) {
        val item = items.firstOrNull { it.id == itemId } ?: return
        item.customerId = customer?.id
        item.customerName = customer?.name
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
    }

    fun increase(id: String) {
        items.firstOrNull { it.id == id }?.let { it.quantity += 1 }
    }

    fun decrease(id: String) {
        val item = items.firstOrNull { it.id == id } ?: return
        item.quantity -= 1
        if (item.quantity <= 0) remove(id)
    }

    fun clear() {
        items.clear()
    }

    fun groupedItems(): List<Pair<Customer?, List<Item>>> {
        val assigned = customers.mapNotNull { customer ->
            val customerItems = items.filter { it.customerId == customer.id }
            if (customerItems.isEmpty()) null else customer to customerItems
        }

        val unassigned = items.filter { it.customerId.isNullOrBlank() }

        return buildList {
            addAll(assigned)
            if (unassigned.isNotEmpty()) add(null to unassigned)
        }
    }

    fun customerCountForCheckout(): Int {
        return items.mapNotNull { it.customerId }.distinct().size
    }

    fun totalLabel(): String {
        val total = items.sumOf { item ->
            parsePrice(item.price) * item.quantity
        }
        return "€" + String.format("%.2f", total).replace(".", ",")
    }

    fun displayPrice(value: String): String {
        val raw = value.trim()
        val number = parsePrice(raw)
        if (number <= 0.0) return raw.ifBlank { "€0,00" }
        return "€" + String.format("%.2f", number).replace(".", ",")
    }

    private fun parsePrice(value: String): Double {
        return value
            .replace(",", ".")
            .replace(Regex("[^0-9.]"), "")
            .toDoubleOrNull() ?: 0.0
    }
}
