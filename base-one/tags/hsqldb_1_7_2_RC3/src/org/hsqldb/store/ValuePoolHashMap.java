/* Copyright (c) 2001-2004, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG, 
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb.store;

/**
 * Subclass of BaseHashMap for maintaining a pool of objects. Supports a
 * range of java.lang.* objects.
 *
 */

/** @todo fredt - check accessCount and reset on each getOrAddXXX() */
public class ValuePoolHashMap extends BaseHashMap {

    public ValuePoolHashMap(int initialCapacity, int maxCapacity,
                            int purgePolicy) throws IllegalArgumentException {

        super(initialCapacity, 1, BaseHashMap.objectKeyOrValue,
              BaseHashMap.noKeyOrValue);

        this.accessTable = new int[initialCapacity];
        this.maxCapacity = maxCapacity;
        this.purgePolicy = purgePolicy;
    }

    public void resetCapacity(int maxCapacity,
                              int purgePolicy)
                              throws IllegalArgumentException {

        if (hashIndex.elementCount > maxCapacity) {
            int surplus = 128 + hashIndex.elementCount - maxCapacity;

            if (surplus > hashIndex.elementCount) {
                surplus = hashIndex.elementCount;
            }

            clear(surplus);
        }

        if (maxCapacity < threshold) {
            rehash(maxCapacity);
        }

        this.maxCapacity = maxCapacity;
        this.purgePolicy = purgePolicy;
    }

    protected Integer getOrAddInteger(int intKey) {

        Integer testValue;
        int     index      = hashIndex.getHashIndex(intKey);
        int     lookup     = hashIndex.hashTable[index];
        int     lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup,
                lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (Integer) objectKeyTable[lookup];

            if (testValue.intValue() == intKey) {
                accessTable[lookup] = accessCount++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddInteger(intKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue              = new Integer(intKey);
        objectKeyTable[lookup] = testValue;
        accessTable[lookup]    = accessCount++;

        return testValue;
    }

    protected Long getOrAddLong(long longKey) {

        Long testValue;
        int index = hashIndex.getHashIndex((int) (longKey
            ^ (longKey >>> 32)));
        int lookup     = hashIndex.hashTable[index];
        int lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup,
                lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (Long) objectKeyTable[lookup];

            if (testValue.longValue() == longKey) {
                accessTable[lookup] = accessCount++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddLong(longKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue              = new Long(longKey);
        objectKeyTable[lookup] = testValue;
        accessTable[lookup]    = accessCount++;

        return testValue;
    }

    /**
     * This is dissimilar to normal hash map get() methods. The key Object
     * should have an equals(String) method which should return true if the
     * key.toString.equals(String) is true. Also the key.hashCode() method
     * must return the same value as key.toString.hashCode().<p>
     *
     * The above is always true when the key is a String. But it means it is
     * possible to submit special keys that fulfill the contract. For example
     * a wrapper around a byte[] can be submitted as key to retrieve either
     * a new String, which is the toString() method of the wrapper, or return
     * an existing String which would be equal to the product of toString().
     *
     * @param key String or other Object with compatible equals(String)
     * and hashCode().
     * @return String from map or a new String
     */
    protected String getOrAddString(Object key) {

        String testValue;
        int    index      = hashIndex.getHashIndex(key.hashCode());
        int    lookup     = hashIndex.hashTable[index];
        int    lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup,
                lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (String) objectKeyTable[lookup];

            if (key.equals(testValue)) {
                accessTable[lookup] = accessCount++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddString(key);
        }

        testValue              = key.toString();
        lookup                 = hashIndex.linkNode(index, lastLookup);
        objectKeyTable[lookup] = testValue;
        accessTable[lookup]    = accessCount++;

        return testValue;
    }

    protected java.sql.Date getOrAddDate(long longKey) {

        java.sql.Date testValue;
        int           hash       = (int) longKey ^ (int) (longKey >> 32);
        int           index      = hashIndex.getHashIndex((int) longKey);
        int           lookup     = hashIndex.hashTable[index];
        int           lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup,
                lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (java.sql.Date) objectKeyTable[lookup];

            if (testValue.getTime() == longKey) {
                accessTable[lookup] = accessCount++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddDate(longKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue              = new java.sql.Date(longKey);
        objectKeyTable[lookup] = testValue;
        accessTable[lookup]    = accessCount++;

        return testValue;
    }

    protected Double getOrAddDouble(long longKey) {

        Double testValue;
        int index = hashIndex.getHashIndex((int) (longKey
            ^ (longKey >>> 32)));
        int lookup     = hashIndex.hashTable[index];
        int lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup,
                lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (Double) objectKeyTable[lookup];

            if (Double.doubleToLongBits(testValue.doubleValue()) == longKey) {
                accessTable[lookup] = accessCount++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddDouble(longKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue              = new Double(Double.longBitsToDouble(longKey));
        objectKeyTable[lookup] = testValue;
        accessTable[lookup]    = accessCount++;

        return testValue;
    }

    protected Object getOrAddObject(Object key) {

        Object testValue;
        int    index      = hashIndex.getHashIndex(key.hashCode());
        int    lookup     = hashIndex.hashTable[index];
        int    lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup,
                lookup = hashIndex.getNextLookup(lookup)) {
            testValue = objectKeyTable[lookup];

            if (testValue.equals(key)) {
                accessTable[lookup] = accessCount++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddObject(key);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        objectKeyTable[lookup] = key;
        accessTable[lookup]    = accessCount++;

        return key;
    }
/*
    public static void main(String[] argv) {

        int                      BIGRANGE   = 100000;
        int                      SMALLRANGE = 50000;
        int                      POOLSIZE   = 100000;
        java.util.Random         randomgen  = new java.util.Random();
        org.hsqldb.lib.StopWatch sw         = new org.hsqldb.lib.StopWatch();
        ValuePoolHashMap map = new ValuePoolHashMap(POOLSIZE, POOLSIZE,
            BaseHashMap.PURGE_HALF);
        int maxCount = 5000000;

        try {
            for (int rounds = 0; rounds < 3; rounds++) {
                sw.zero();

                // timing for ValuePool retreival
                for (int i = 0; i < maxCount; i++) {
                    boolean bigrange  = (i % 2) == 0;
                    int     intValue  = randomgen.nextInt(bigrange ? BIGRANGE
                                                                   : SMALLRANGE);
                    Integer intObject = map.getOrAddInteger(intValue);

                    if (intObject.intValue() != intValue) {
                        throw new Exception("Value mismatch");
                    }

                    //                    System.out.print(intValue);
                    //                    System.out.print(' ');
                }

                System.out.println("Count " + maxCount + " "
                                   + sw.elapsedTime());
                sw.zero();

                // timing for Integer creation
                for (int i = 0; i < maxCount; i++) {
                    boolean bigrange  = (i % 2) == 0;
                    int     intValue  = randomgen.nextInt(bigrange ? BIGRANGE
                                                                   : SMALLRANGE);
                    Integer intObject = new Integer(intValue);

                    if (intObject.intValue() != intValue) {
                        throw new Exception("Value mismatch");
                    }
                }

                System.out.println("Count new Integer() " + maxCount + " "
                                   + sw.elapsedTime());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/
}
