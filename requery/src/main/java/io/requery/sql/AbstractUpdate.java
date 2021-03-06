/*
 * Copyright 2016 requery.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.requery.sql;

import io.requery.meta.Attribute;
import io.requery.proxy.EntityProxy;
import io.requery.query.Expression;
import io.requery.util.function.Function;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for update operation executors.
 *
 * @author Nikhil Purushe
 */
abstract class AbstractUpdate {

    protected final RuntimeConfiguration configuration;
    protected final GeneratedResultReader generatedResultReader;

    protected AbstractUpdate(RuntimeConfiguration configuration,
                             GeneratedResultReader generatedResultReader) {
        this.configuration = configuration;
        this.generatedResultReader = generatedResultReader;
    }

    protected PreparedStatement prepare(String sql, Connection connection) throws SQLException {
        PreparedStatement statement;
        if (generatedResultReader != null) {
            if (configuration.platform().supportsGeneratedColumnsInPrepareStatement()) {
                String[] generatedColumns = generatedResultReader.generatedColumns();
                statement = connection.prepareStatement(sql, generatedColumns);
            } else {
                statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
        } else {
            statement = connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS);
        }
        return statement;
    }

    protected void mapParameters(PreparedStatement statement, BoundParameters parameters)
        throws SQLException {

        for (int i = 0; i < parameters.count(); i++) {
            Expression expression = parameters.expressionAt(i);
            Object value = parameters.valueAt(i);
            if (expression instanceof Attribute) {
                Attribute attribute = (Attribute) expression;
                if (attribute.isAssociation()) {
                    // get the referenced value
                    if (value != null) {
                        Attribute<Object, Object> referenced =
                            Attributes.get(attribute.referencedAttribute());
                        Function<Object, EntityProxy<Object>> proxyProvider =
                            referenced.declaringType().proxyProvider();
                        value = proxyProvider.apply(value).get(referenced);
                    }
                }
            }
            configuration.mapping().write(expression, statement, i + 1, value);
        }
    }

    protected void readGeneratedKeys(int index, Statement statement) throws SQLException {
        if (generatedResultReader != null) {
            try (ResultSet results = statement.getGeneratedKeys()) {
                generatedResultReader.read(index, results);
            }
        }
    }
}
