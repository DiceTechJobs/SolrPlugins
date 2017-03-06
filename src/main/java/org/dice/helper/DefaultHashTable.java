package org.dice.helper;

import java.util.Hashtable;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by simon.hughes on 2/4/14.
 */
public class DefaultHashTable<K,V> extends Hashtable<K,V> {

    private final Supplier<V> _supplier;
    public DefaultHashTable(V defaultVal)
    {
        // need to make this final
        final V defaultValCopy = defaultVal;
        _supplier = new Supplier<V>() {
            public V get() {
                return defaultValCopy;
            }
        };
    }

    public DefaultHashTable(V defaultVal, Map<K,V> map){
        super(map);
        final V defaultValCopy = defaultVal;
        _supplier = new Supplier<V>() {
            @Override
            public V get() {
                return defaultValCopy;
            }
        };
    }

    public DefaultHashTable(Supplier<V> supplier)
    {
        _supplier = supplier;
    }

    public DefaultHashTable(Supplier<V> supplier, Map<K,V> map)
    {
        super(map);
        _supplier = supplier;
    }

    @Override
    public V get(Object key)
    {
        if(!this.containsKey(key))
        {
            super.put((K)key, this._supplier.get());
        }
        return super.get(key);
    }
}