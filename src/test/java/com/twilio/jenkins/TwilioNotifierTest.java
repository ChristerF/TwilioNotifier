package com.twilio.jenkins;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TwilioNotifierTest {

    @Test
    public void testParsingUserList() {
        
        Map<String,Pair<String,String>> result = TwilioNotifier.parseUserList("test.user:415 555 5555:Test User,C:D:E,E:F:G");
        assertTrue(result.get("test.user").equals(new Pair<String,String>("415 555 5555", "Test User")));
        assertTrue(result.get("C").equals(new Pair<String,String>("D", "E")));
        assertTrue(result.get("E").equals(new Pair<String,String>("F", "G")));
        
       
    }
    
    @Test
    public void testSubstitution()
    {
        Map<String,String> subMap = new HashMap<String,String>();
        subMap.put("%CULPRIT%","Christer");
        subMap.put("%PROJECT%","TwilioNotifier");
        String input = "Dear %CULPRIT%, your project %PROJECT% is failing.";
        String result = TwilioNotifier.substituteAttributes(input, subMap);
        assertEquals(result,"Dear Christer, your project TwilioNotifier is failing.");
    }
    
    @Test
    public void testCulpritListToString()
    {
        List<Pair<String, String>> phoneToCulprit = new ArrayList<Pair<String,String>>();
        phoneToCulprit.add(new Pair<String,String>("4155555555","William"));
        phoneToCulprit.add(new Pair<String,String>("4155555556","James"));
        String result = TwilioNotifier.culpritStringFromList(phoneToCulprit);
        assertEquals(result,"William and James");
        
        phoneToCulprit = new ArrayList<Pair<String,String>>();
        phoneToCulprit.add(new Pair<String,String>("4155555556","James"));
         result = TwilioNotifier.culpritStringFromList(phoneToCulprit);
        assertEquals(result,"James");
        
        phoneToCulprit = new ArrayList<Pair<String,String>>();
        phoneToCulprit.add(new Pair<String,String>("4155555555","William"));
        phoneToCulprit.add(new Pair<String,String>("4155555556","James"));
        phoneToCulprit.add(new Pair<String,String>("4155555557","Luke"));
          result = TwilioNotifier.culpritStringFromList(phoneToCulprit);
        assertEquals(result,"William James and Luke");
        
        
    }

}
