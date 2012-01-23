package com.twilio.jenkins;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

import hudson.model.ModelObject;
import hudson.model.User;
import org.junit.Test;

public class TwilioNotifierTest {
    @Test
    public void testSubstitution() {
        Map<String,String> subMap = new HashMap<String,String>();
        subMap.put("%CULPRIT%","Christer");
        subMap.put("%PROJECT%","TwilioNotifier");
        String input = "Dear %CULPRIT%, your project %PROJECT% is failing.";
        String result = TwilioNotifier.substituteAttributes(input, subMap);
        assertEquals(result,"Dear Christer, your project TwilioNotifier is failing.");
    }

    @Test
    public void testCulpritListToString() {
		//Using ModelObject since I can't find a way to create User Objects
		ModelObject william = createModelObject("William");
		ModelObject james = createModelObject("James");
		ModelObject luke = createModelObject("Luke");

		String result;

        result = TwilioNotifier.culpritStringFromList(Arrays.asList(william, james));
        assertEquals(result,"William and James");

        result = TwilioNotifier.culpritStringFromList(Arrays.asList(james));
        assertEquals(result,"James");

        result = TwilioNotifier.culpritStringFromList(Arrays.asList(william, james, luke));
        assertEquals(result,"William James and Luke");
	}

	private ModelObject createModelObject(final String name) {
		return new ModelObject() {
			public String getDisplayName() {
				return name;
			}
		};
	}
}
