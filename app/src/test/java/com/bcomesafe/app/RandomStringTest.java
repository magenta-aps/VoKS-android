package com.bcomesafe.app;

import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

import com.bcomesafe.app.utils.RandomString;

import static org.junit.Assert.assertTrue;

@SmallTest
public class RandomStringTest {
    @Test
    public void testRandomString_lengthIsNegative_lengthShouldBeDefault() {
        System.out.println("---------------------------------------------------------------------");
        System.out.println("Running testRandomString_lengthIsNegative_lengthShouldBeDefault");
        boolean result = new RandomString(-1).nextString().length() == RandomString.DEFAULT_RANDOM_LENGTH;
        System.out.println("Result=" + result);
        assertTrue(result);
        System.out.println("Finished testRandomString_lengthIsNegative_lengthShouldBeDefault");
    }

    @Test
    public void testRandomString_lengthIsZero_lengthShouldBeDefault() {
        System.out.println("---------------------------------------------------------------------");
        System.out.println("Running testRandomString_lengthIsZero_lengthShouldBeDefault");
        boolean result = new RandomString(0).nextString().length() == RandomString.DEFAULT_RANDOM_LENGTH;
        System.out.println("Result=" + result);
        assertTrue(result);
        System.out.println("Finished testRandomString_lengthIsZero_lengthShouldBeDefault");
    }

    @Test
    public void testRandomString_lengthIsPositive_lengthShouldBeAsGiven() {
        System.out.println("---------------------------------------------------------------------");
        System.out.println("Running testRandomString_lengthIsPositive_lengthShouldBeAsGiven");
        int givenLength = 10;
        boolean result = new RandomString(givenLength).nextString().length() == givenLength;
        System.out.println("Result=" + result);
        assertTrue(result);
        System.out.println("Finished testRandomString_lengthIsPositive_lengthShouldBeAsGiven");
    }
}
