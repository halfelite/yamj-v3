/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.hibernate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.*;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;
import org.hibernate.type.BasicType;
import org.springframework.beans.factory.annotation.Autowired;
import org.yamj.core.api.model.builder.SqlScalars;
import org.yamj.core.api.options.IOptions;
import org.yamj.core.api.wrapper.IApiWrapper;

/**
 * Hibernate DAO implementation
 */
public abstract class HibernateDao {

    @Autowired
    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Flush and clear the session.
     */
    public void flushAndClear() {
        currentSession().flush();
        currentSession().clear();
    }

    /**
     * Store an entity.
     *
     * @param entity the entity to store
     */
    public void storeEntity(final Object entity) {
        currentSession().saveOrUpdate(entity);
    }

    /**
     * Merge an entity.
     *
     * @param entity the entity to merge
     */
    public void mergeEntity(final Object entity) {
        currentSession().merge(entity);
    }

    /**
     * Store all entities.
     *
     * @param entities the entities to store
     */
    @SuppressWarnings("rawtypes")
    public void storeAll(final Collection entities) {
        if (entities != null && !entities.isEmpty()) {
            for (Object entity : entities) {
                currentSession().saveOrUpdate(entity);
            }
        }
    }

    /**
     * Save an entity.
     *
     * @param entity the entity to save
     */
    public void saveEntity(final Object entity) {
        currentSession().save(entity);
    }

    /**
     * Update an entity.
     *
     * @param entity the entity to update
     */
    public void updateEntity(final Object entity) {
        currentSession().update(entity);
    }

    /**
     * Delete an entity.
     *
     * @param entity the entity to delete
     */
    public void deleteEntity(final Object entity) {
        currentSession().delete(entity);
    }

    /**
     * Get a single object by its id
     *
     * @param entityClass
     * @param id
     * @return
     */
    public <T> T getById(Class<T> entityClass, Serializable id) {
        return currentSession().get(entityClass, id);
    }

    /**
     * Get all objects for the entity class in a defined order.
     *
     * @param entityClass
     * @param order
     * @return
     */
    @SuppressWarnings("unchecked")
	public <T> List<T> getAll(Class<T> entityClass, String order) {
        return currentSession().createCriteria(entityClass).addOrder(Order.asc(order)).list();
    }

    /**
     * Get a single object by the passed field using the name case sensitive.
     *
     * @param entityClass
     * @param field
     * @param name
     * @return
     */
    public <T> T getByNaturalId(Class<T> entityClass, String field, String name) {
        return currentSession().byNaturalId(entityClass).using(field, name).load();
    }

    /**
     * Get a single object by the passed field using the name case insensitive.
     *
     * @param entityClass
     * @param field
     * @param name
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getByNaturalIdCaseInsensitive(Class<T> entityClass, String field, String name) {
        return (T) currentSession().createCriteria(entityClass).add(Restrictions.ilike(field, name, MatchMode.EXACT)).uniqueResult();
    }

    /**
     * Convert row object to a string.
     *
     * @param rowElement
     * @return <code>String</code>
     */
    protected static String convertRowElementToString(Object rowElement) {
        final String result;
        if (rowElement == null) {
            result = null;
        } else if (rowElement instanceof String) {
            result = (String) rowElement;
        } else {
            result = rowElement.toString();
        }
        return result;
    }

    /**
     * Convert row object to Integer.
     *
     * @param rowElement
     * @return <code>Integer</code>
     */
    protected static Integer convertRowElementToInteger(Object rowElement) {
        final Integer result;
        if (rowElement == null) {
            result = Integer.valueOf(0);
        } else if (rowElement instanceof Integer) {
            result = (Integer) rowElement;
        } else if (rowElement instanceof BigInteger) {
            result = ((BigInteger) rowElement).intValue();
        } else if (rowElement instanceof Long) {
            result = ((Long) rowElement).intValue();
        } else if (StringUtils.isNumeric(rowElement.toString())) {
            result = Integer.valueOf(rowElement.toString());
        } else {
            result = Integer.valueOf(0);
        }
        return result;
    }

    /**
     * Convert row object to Long.
     *
     * @param rowElement
     * @return <code>Long</code>
     */
    protected static Long convertRowElementToLong(Object rowElement) {
        final Long result;
        if (rowElement == null) {
            result = Long.valueOf(0);
        } else if (rowElement instanceof BigInteger) {
            result = ((BigInteger) rowElement).longValue();
        } else if (rowElement instanceof Long) {
            result = (Long) rowElement;
        } else if (rowElement instanceof Integer) {
            result = ((Integer) rowElement).longValue();
        } else if (StringUtils.isNumeric(rowElement.toString())) {
            result = Long.valueOf(rowElement.toString());
        } else {
            result = Long.valueOf(0);
        }
        return result;
    }

    /**
     * Convert row object to date.
     *
     * @param rowElement
     * @return
     */
    protected static Date convertRowElementToDate(Object rowElement) {
        final Date result;
        if (rowElement == null) {
            result = null;
        } else if (rowElement instanceof Date) {
            result = (Date) rowElement;
        } else if (rowElement instanceof Timestamp) {
            result =  (Date) rowElement;
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Convert row object to date.
     *
     * @param rowElement
     * @return
     */
    protected static Timestamp convertRowElementToTimestamp(Object rowElement) {
        final Timestamp result;
        if (rowElement == null) {
            result = null;
        } else if (rowElement instanceof Timestamp) {
            result = (Timestamp) rowElement;
        } else if (rowElement instanceof Date) {
            result = new Timestamp(((Date)rowElement).getTime());
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Convert row object to big decimal.
     *
     * @param rowElement
     * @return <code>BigDecimal</code>
     */
    protected static BigDecimal convertRowElementToBigDecimal(Object rowElement) {
        final BigDecimal result;
        if (rowElement == null) {
            result = BigDecimal.ZERO;
        } else if (rowElement instanceof BigDecimal) {
            result = (BigDecimal) rowElement;
        } else {
            result = new BigDecimal(rowElement.toString());
        }
        return result;
    }

    /**
     * Convert row object to boolean.
     *
     * @param rowElement
     * @return <code>BigDecimal</code>
     */
    protected static Boolean convertRowElementToBoolean(Object rowElement) {
        final Boolean result;
        if (rowElement == null) {
            result =  Boolean.FALSE;
        } else if (rowElement instanceof Boolean) {
            result =  (Boolean) rowElement;
        } else if ("1".equals(rowElement.toString())) {
            result =  Boolean.TRUE;
        } else if ("true".equals(rowElement.toString())) {
            result =  Boolean.TRUE;
        } else {
            result =  Boolean.FALSE;
        }
        return result;
    }

    private static void applyNamedParameters(Query queryObject, Map<String, Object> params) throws HibernateException {
        for (Entry<String, Object> param : params.entrySet()) {
            applyNamedParameterToQuery(queryObject, param.getKey(), param.getValue());
        }
    }

    @SuppressWarnings("rawtypes")
    private static void applyNamedParameterToQuery(Query queryObject, String paramName, Object value) throws HibernateException {
        if (value instanceof Collection) {
            queryObject.setParameterList(paramName, (Collection) value);
        } else if (value instanceof Object[]) {
            queryObject.setParameterList(paramName, (Object[]) value);
        } else if (value instanceof String) {
            queryObject.setString(paramName, (String) value);
        } else {
            queryObject.setParameter(paramName, value);
        }
    }

    /**
     * Find list of entities by named parameters.
     *
     * @param queryCharSequence the query string
     * @param params the named parameters
     * @return list of entities
     */
    @SuppressWarnings("rawtypes")
    public List findByNamedParameters(CharSequence queryCharSequence, Map<String, Object> params) {
        Query query = currentSession().createQuery(queryCharSequence.toString()).setCacheable(true);
        applyNamedParameters(query, params);
        return query.list();
    }

    /**
     * Find unique entity by named parameters.
     *
     * @param entityClass the entity class
     * @param queryCharSequence the query string
     * @param params the named parameters
     * @return list of entities
     */
    @SuppressWarnings("unchecked")
    public <T> T findUniqueByNamedParameters(Class<T> entityClass, CharSequence queryCharSequence, Map<String, Object> params) { //NOSONAR
        Query query = currentSession().createQuery(queryCharSequence.toString()).setCacheable(true);
        applyNamedParameters(query, params);
        return (T)query.uniqueResult();
    }

    /**
     * Find entries using a named query.
     *
     * @param queryName the name of the query
     * @return list of entities
     */
    @SuppressWarnings("rawtypes")
    public List namedQuery(String queryName) {
        return currentSession().getNamedQuery(queryName).setCacheable(true).list();
    }

    /**
     * Find entries by id using a named query.
     *
     * @param queryName the name of the query
     * @param id the id
     * @return list of entities
     */
    @SuppressWarnings("rawtypes")
    public List namedQueryById(String queryName, long id) {
        return currentSession().getNamedQuery(queryName).setLong("id", id).setCacheable(true).list();
    }

    /**
     * Find list of entities by named parameters using a named query.
     *
     * @param queryName the name of the query
     * @param params the named parameters
     * @return list of entities
     */
    @SuppressWarnings("rawtypes")
    public List namedQueryByNamedParameters(String queryName, Map<String, Object> params) {
        Query query = currentSession().getNamedQuery(queryName).setCacheable(true);
        applyNamedParameters(query, params);
        return query.list();
    }

    /**
     * Find list of entities by named parameters using a named query.
     *
     * @param queryName the name of the query
     * @param params the named parameters
     * @param maxResults the maximal amount of entities to fetch
     * @return list of entities
     */
    @SuppressWarnings("rawtypes")
    public List namedQueryByNamedParameters(String queryName, Map<String, Object> params, int maxResults) {
        Query query = currentSession().getNamedQuery(queryName).setCacheable(true);
        applyNamedParameters(query, params);
        query.setMaxResults(maxResults);
        return query.list();
    }

    /**
     * Execute an update statement with a named query.
     *
     * @param queryName the name of the query
     * @return number of affected rows
     */
    public int executeUpdate(String queryName) {
        return currentSession().getNamedQuery(queryName).setCacheable(true).executeUpdate();
    }

    /**
     * Execute an update statement.
     *
     * @param queryName the name of the query
     * @param params the named parameters
     * @return number of affected rows
     */
    public int executeUpdate(String queryName, Map<String, Object> params) {
        Query query = currentSession().getNamedQuery(queryName).setCacheable(true);
        applyNamedParameters(query, params);
        return query.executeUpdate();
    }

    /**
     * Execute a query to return the results
     *
     * Gets the options from the wrapper for start and max
     *
     * @param entityClass
     * @param sqlScalars
     * @return
     */
    public <T> List<T> executeQueryWithTransform(Class<T> entityClass, SqlScalars sqlScalars) { //NOSONAR
        return this.executeQueryWithTransform(entityClass, sqlScalars, null);
    }

    /**
     * Execute a query to return the results
     *
     * Gets the options from the wrapper for start and max
     *
     * Puts the total count returned from the query into the wrapper
     *
     * @param entityClass
     * @param sqlScalars
     * @param wrapper
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T> List<T> executeQueryWithTransform(Class<T> entityClass, SqlScalars sqlScalars, IApiWrapper wrapper) { //NOSONAR
        
        SQLQuery query = currentSession().createSQLQuery(sqlScalars.getSql());
        query.setReadOnly(true).setCacheable(true);
        
        // add parameters
        for (Map.Entry<String, Object> entry : sqlScalars.getParameters().entrySet()) {
            if (entry.getValue() instanceof Collection) {
                query.setParameterList(entry.getKey(), (Collection) entry.getValue());
            } else if (entry.getValue() instanceof Object[]) {
                query.setParameterList(entry.getKey(), (Object[]) entry.getValue());
            } else {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }

        // populate scalars
        for (Map.Entry<String, BasicType> entry : sqlScalars.getScalars().entrySet()) {
            if (entry.getValue() == null) {
                // use the default scalar for that entry
                query.addScalar(entry.getKey());
            } else {
                // use the passed scalar type
                query.addScalar(entry.getKey(), entry.getValue());
            }
        }

        if (entityClass.equals(String.class) || entityClass.equals(Long.class) || entityClass.equals(Integer.class)) {
            // no transformer needed
        } else if (entityClass.equals(Object[].class)) {
            // no transformer needed
        } else {
            query.setResultTransformer(Transformers.aliasToBean(entityClass));
        }

        // run query
		List<T> queryResults = query.list();

        // if the wrapper is populated, then run the query to get the maximum results
        if (wrapper != null) {
            wrapper.setTotalCount(queryResults.size());

            // if there is a start or max set, we will need to re-run the query after setting the options
            IOptions options = wrapper.getOptions();
            if (options != null && (options.getStart() > 0 || options.getMax() > 0)) {
                if (options.getStart() > 0) {
                    query.setFirstResult(options.getStart());
                }

                if (options.getMax() > 0) {
                    query.setMaxResults(options.getMax());
                }
                
                // this will get the trimmed list
                queryResults = query.list();
            }
        }

        return queryResults;
    }
}
