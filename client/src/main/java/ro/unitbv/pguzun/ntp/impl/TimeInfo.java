package ro.unitbv.pguzun.ntp.impl;

import java.util.ArrayList; 
import java.util.List; 


public class TimeInfo { 

   private final NtpV3Packet _message; 
   private List<String> _comments; 
   private Long _delay; 
   private Long _offset; 

   private final long _returnTime; 

   private boolean _detailsComputed; 

   public TimeInfo(NtpV3Packet message, long returnTime) { 
       this(message, returnTime, null, true); 
   } 

   public TimeInfo(NtpV3Packet message, long returnTime, List<String> comments) 
   { 
           this(message, returnTime, comments, true); 
   } 

   public TimeInfo(NtpV3Packet msgPacket, long returnTime, boolean doComputeDetails) 
   { 
           this(msgPacket, returnTime, null, doComputeDetails); 
   } 

   public TimeInfo(NtpV3Packet message, long returnTime, List<String> comments, 
                  boolean doComputeDetails) 
   { 
       if (message == null) { 
           throw new IllegalArgumentException("message cannot be null"); 
       } 
       this._returnTime = returnTime; 
       this._message = message; 
       this._comments = comments; 
       if (doComputeDetails) { 
           computeDetails(); 
       } 
   } 

   public void addComment(String comment) 
   { 
       if (_comments == null) { 
           _comments = new ArrayList<String>(); 
       } 
       _comments.add(comment); 
   } 

   /**
    * Compute and validate details of the NTP message packet. Computed 
    * fields include the offset and delay. 
    */ 
   public void computeDetails() 
   { 
       if (_detailsComputed) { 
           return; // details already computed - do nothing 
       } 
       _detailsComputed = true; 
       if (_comments == null) { 
           _comments = new ArrayList<String>(); 
       } 

       TimeStamp origNtpTime = _message.getOriginateTimeStamp(); 
       long origTime = origNtpTime.getTime(); 

       // Receive Time is time request received by server (t2) 
       TimeStamp rcvNtpTime = _message.getReceiveTimeStamp(); 
       long rcvTime = rcvNtpTime.getTime(); 

       // Transmit time is time reply sent by server (t3) 
       TimeStamp xmitNtpTime = _message.getTransmitTimeStamp(); 
       long xmitTime = xmitNtpTime.getTime(); 

       /*
        * Round-trip network delay and local clock offset (or time drift) is calculated 
        * according to this standard NTP equation: 
        * 
        * LocalClockOffset = ((ReceiveTimestamp - OriginateTimestamp) + 
        *                     (TransmitTimestamp - DestinationTimestamp)) / 2 
        * 
        * equations from RFC-1305 (NTPv3) 
        *      roundtrip delay = (t4 - t1) - (t3 - t2) 
        *      local clock offset = ((t2 - t1) + (t3 - t4)) / 2 
        * 
        * It takes into account network delays and assumes that they are symmetrical. 
        * 
        * Note the typo in SNTP RFCs 1769/2030 which state that the delay 
        * is (T4 - T1) - (T2 - T3) with the "T2" and "T3" switched. 
        */ 
       if (origNtpTime.ntpValue() == 0) 
       { 
           // without originate time cannot determine when packet went out 
           // might be via a broadcast NTP packet... 
           if (xmitNtpTime.ntpValue() != 0) 
           { 
               _offset = Long.valueOf(xmitTime - _returnTime); 
               _comments.add("Error: zero orig time -- cannot compute delay"); 
           } else { 
               _comments.add("Error: zero orig time -- cannot compute delay/offset"); 
           } 
       } else if (rcvNtpTime.ntpValue() == 0 || xmitNtpTime.ntpValue() == 0) { 
           _comments.add("Warning: zero rcvNtpTime or xmitNtpTime"); 
           // assert destTime >= origTime since network delay cannot be negative 
           if (origTime > _returnTime) { 
               _comments.add("Error: OrigTime > DestRcvTime"); 
           } else { 
               // without receive or xmit time cannot figure out processing time 
               // so delay is simply the network travel time 
               _delay = Long.valueOf(_returnTime - origTime); 
           } 
           // TODO: is offset still valid if rcvNtpTime=0 || xmitNtpTime=0 ??? 
           // Could always hash origNtpTime (sendTime) but if host doesn't set it 
           // then it's an malformed ntp host anyway and we don't care? 
           // If server is in broadcast mode then we never send out a query in first place... 
           if (rcvNtpTime.ntpValue() != 0) 
           { 
               // xmitTime is 0 just use rcv time 
               _offset = Long.valueOf(rcvTime - origTime); 
           } else if (xmitNtpTime.ntpValue() != 0) 
           { 
               // rcvTime is 0 just use xmitTime time 
               _offset = Long.valueOf(xmitTime - _returnTime); 
           } 
       } else 
       { 
            long delayValue = _returnTime - origTime; 
            // assert xmitTime >= rcvTime: difference typically < 1ms 
            if (xmitTime < rcvTime) 
            { 
                // server cannot send out a packet before receiving it... 
                _comments.add("Error: xmitTime < rcvTime"); // time-travel not allowed 
            } else 
            { 
                // subtract processing time from round-trip network delay 
                long delta = xmitTime - rcvTime; 
                // in normal cases the processing delta is less than 
                // the total roundtrip network travel time. 
                if (delta <= delayValue) 
                { 
                    delayValue -= delta; // delay = (t4 - t1) - (t3 - t2) 
                } else 
                { 
                    // if delta - delayValue == 1 ms then it's a round-off error 
                    // e.g. delay=3ms, processing=4ms 
                    if (delta - delayValue == 1) 
                    { 
                        // delayValue == 0 -> local clock saw no tick change but destination clock did 
                        if (delayValue != 0) 
                        { 
                            _comments.add("Info: processing time > total network time by 1 ms -> assume zero delay"); 
                            delayValue = 0; 
                        } 
                    } else { 
                       _comments.add("Warning: processing time > total network time"); 
                   } 
                } 
            } 
            _delay = Long.valueOf(delayValue); 
           if (origTime > _returnTime) { 
               _comments.add("Error: OrigTime > DestRcvTime"); 
           } 

           _offset = Long.valueOf(((rcvTime - origTime) + (xmitTime - _returnTime)) / 2); 
       } 
   } 

   public List<String> getComments() 
   { 
       return _comments; 
   } 

   public Long getDelay() 
   { 
       return _delay; 
   } 

   public Long getOffset() 
   { 
       return _offset; 
   } 

   public NtpV3Packet getMessage() 
   { 
       return _message; 
   } 

   public long getReturnTime() 
   { 
       return _returnTime; 
   } 

}