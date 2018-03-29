/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.work;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import java.io.IOException;

public class DataTest {
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";

    @Test
    public void testSize_noArguments() {
        Data data = new Data.Builder().build();
        assertThat(data.size(), is(0));
    }

    @Test
    public void testSize_hasArguments() {
        Data data = new Data.Builder().putBoolean(KEY1, true).build();
        assertThat(data.size(), is(1));
    }

    @Test
    public void testSerializeEmpty() throws IOException, ClassNotFoundException {
        Data data = Data.EMPTY;

        byte[] byteArray = Data.toByteArray(data);
        Data restoredData = Data.fromByteArray(byteArray);

        assertThat(restoredData, is(data));
    }

    @Test
    public void testSerializeString() throws IOException, ClassNotFoundException {
        String expectedValue1 = "value1";
        String expectedValue2 = "value2";
        Data data = new Data.Builder()
                .putString(KEY1, expectedValue1)
                .putString(KEY2, expectedValue2)
                .build();

        byte[] byteArray = Data.toByteArray(data);
        Data restoredData = Data.fromByteArray(byteArray);

        assertThat(restoredData, is(data));
    }

    @Test
    public void testSerializeIntArray() throws IOException, ClassNotFoundException {
        int[] expectedValue1 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int[] expectedValue2 = new int[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        Data data = new Data.Builder()
                .putIntArray(KEY1, expectedValue1)
                .putIntArray(KEY2, expectedValue2)
                .build();

        byte[] byteArray = Data.toByteArray(data);
        Data restoredData = Data.fromByteArray(byteArray);

        assertThat(restoredData, is(notNullValue()));
        assertThat(restoredData.size(), is(2));
        assertThat(restoredData.getIntArray(KEY1), is(equalTo(expectedValue1)));
        assertThat(restoredData.getIntArray(KEY2), is(equalTo(expectedValue2)));
    }
}
