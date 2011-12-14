package com.twilio.jenkins;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TwilioNotifierTest {

    @Test
    public void testParsingUserList() {
        
        Map<String,String> result = TwilioNotifier.parseUserList("A:B,C:D,E:F");
        assertTrue(result.get("A").equals("B"));
        assertTrue(result.get("C").equals("D"));
        assertTrue(result.get("E").equals("F"));
        
       
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
