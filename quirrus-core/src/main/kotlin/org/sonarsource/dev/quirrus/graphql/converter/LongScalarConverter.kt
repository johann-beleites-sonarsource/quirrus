package org.sonarsource.dev.quirrus.graphql.converter

import com.expediagroup.graphql.client.converter.ScalarConverter

class LongScalarConverter : ScalarConverter<Long> {
    override fun toScalar(rawValue: Any): Long {
        return rawValue.toString().toLong()
    }

    override fun toJson(value: Long): Any {
        return value
    }
}