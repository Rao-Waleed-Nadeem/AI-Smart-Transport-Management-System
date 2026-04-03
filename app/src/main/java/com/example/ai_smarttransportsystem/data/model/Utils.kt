package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.firestore.PropertyName

data class Utils(
    @get:PropertyName("bus_average")
    @set:PropertyName("bus_average")
    var busAverage: Double? = 0.0,

    @get:PropertyName("days_in_month")
    @set:PropertyName("days_in_month")
    var daysInMonth: Long? = 0,

    @get:PropertyName("is_optimized")
    @set:PropertyName("is_optimized")
    var isOptimized: Boolean? = false,

    @get:PropertyName("petrol_price_per_litre")
    @set:PropertyName("petrol_price_per_litre")
    var petrolPricePerLitre: Double? = 0.0,

    @get:PropertyName("profit_margin_percent")
    @set:PropertyName("profit_margin_percent")
    var profitMarginPercent: Double? = 0.0,

    @get:PropertyName("semester_months")
    @set:PropertyName("semester_months")
    var semesterMonths: Double? = 0.0,

    @get:PropertyName("university_latitude")
    @set:PropertyName("university_latitude")
    var universityLatitude: Double? = 0.0,

    @get:PropertyName("university_longitude")
    @set:PropertyName("university_longitude")
    var universityLongitude: Double? = 0.0,
)
