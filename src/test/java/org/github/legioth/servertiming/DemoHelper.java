package org.github.legioth.servertiming;

import java.util.Arrays;
import java.util.stream.Stream;

import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.function.SerializablePredicate;

public class DemoHelper {
    public static class SlowListDataProvider<T> extends ListDataProvider<T> {
        @SafeVarargs SlowListDataProvider(T... items) {
            super(Arrays.asList(items));
        }
    
        @Override
        public Stream<T> fetch(Query<T, SerializablePredicate<T>> query) {
            sleep(500);
            return super.fetch(query);
        }
    
        @Override
        public int size(Query<T, SerializablePredicate<T>> query) {
            sleep(250);
            return super.size(query);
        }
    }

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    public static SlowListDataProvider<String> slowDataProvider(String... strings) {
        return new SlowListDataProvider<>(strings);
    }
}
