package com.twilio.jenkins;

import static org.junit.Assert.*;

import java.util.HashMap;
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

}
