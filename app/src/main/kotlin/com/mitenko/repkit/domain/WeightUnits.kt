package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightUnit
import java.util.Locale

/** The countries that weigh in pounds (spec rev 26 §5): the United States, Liberia and Myanmar. */
private val POUND_COUNTRIES = setOf("US", "LR", "MM")

/** Spec rev 26 §5: LB when [country] (ISO 3166 alpha-2, any case) is in POUND_COUNTRIES, otherwise KG, including for "". */
fun defaultWeightUnit(country: String): WeightUnit =
    if (country.uppercase(Locale.ROOT) in POUND_COUNTRIES) WeightUnit.LB else WeightUnit.KG
