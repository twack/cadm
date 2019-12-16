/**
 * Copyright Smartport LLC 2019 
 *
 *
 *
 Changes:
 *
 *  V1.3.1 - Added App Description fiekd 
 *  V1.3.0 - Added different timeouts for different modes
 *
 *  V1.2.0 - Set default values for timeouts, Set default value for contact
 *  V1.1.0 - Changed where to store variable (on the hub) - This will need to be done remotely at some point.
 *  V1.0.0 - POC
 *
 */



definition(
    name: "Lock Guardian",
    namespace: "Smartport",
    author: '{"authorName": "AJ Parker", "smartportVersion": "1.3.1"}',
    description: "Autolock Lock Apartment Front Door",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {

	section {
    
    
     	input(name: "lock1", type: "capability.lock", title: "Door Lock", required: true, multiple: false, description: null)
		input(name: "contact1", type: "capability.contactSensor", title: "Door Contact", required: true, multiple: false, description: null) 
    }
    section("<span style=\"color:#27B727\">Day Mode Settings</span>") {
        input(name: "lockTime1", type: "number", title: "How long to wait for autolock (If contact closed)", required: true, defaultValue: 30)
    	input(name: "holdtime1", type: "number", title: "After contact closes, wait this number of minutes before autolock", required: true, defaultValue: 2)
    }
    section("<span style=\"color:#27B727\">Evening Mode Settings</span>") {
        input(name: "lockTime2", type: "number", title: "How long to wait for autolock (If contact closed)", required: true, defaultValue: 15)
    	input(name: "holdtime2", type: "number", title: "After contact closes, wait this number of minutes before autolock", required: true, defaultValue: 2)
    }
    section("<span style=\"color:#27B727\">Night Mode Settings</span>") {
        input(name: "lockTime3", type: "number", title: "How long to wait for autolock (If contact closed)", required: true, defaultValue: 5)
    	input(name: "holdtime3", type: "number", title: "After contact closes, wait this number of minutes before autolock", required: true, defaultValue: 2)
        
}   
 section("Logging"){
            input "debugmode", "bool", title: "Enable logging", required: true, defaultValue: false
        }

}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
setAppVersion()
//   setVariables()
    setTimeouts()
    
log.debug "Initialised with settings: ${settings}"
	subscribe(lock1, "lock", lockHandler)
    subscribe(contact1, "contact", contactHandler)
    
    
//    state.delay1 = 60* (location.autoLockTimeout3)
//    state.delay2 = 60* (location.autoLockContactTimeout3)
    state.contactStatus = "closed"  
    
   }
 
def setVariables(){ // Temp way of setting variables
    createLocationVariable("autoLockTimeout3")
    createLocationVariable("autoLockContactTimeout3")
    sendLocationEvent(name: "autoLockTimeout3", value: 30)
    sendLocationEvent(name: "autoLockContactTimeout3", value: 2)
    
   
}

def setTimeouts(){
    LOGDEBUG("Checking mode for timeout settings") 
    if(location.mode.contains("ay")){ LOGDEBUG("Mode = Day"); state.delay1 = 60* lockTime1; state.delay2 = 60* holdtime1}                              
    if(location.mode.contains("ing")){ LOGDEBUG("Mode = Evening"); state.delay1 = 60* lockTime2; state.delay2 = 60* holdtime2}
    if(location.mode.contains("ght")){ LOGDEBUG("Mode = Night"); state.delay1 = 60* lockTime3; state.delay2 = 60* holdtime3}
     LOGDEBUG("Timeout settings: Autolock: $state.delay1 - ContactLock = $state.delay2") 
 }   
    


def contactHandler(evt){
state.contactStatus = evt.value
LOGDEBUG("$contact1 is $state.contactStatus")
    if(state.contactStatus == "closed" && state.open == true){   
    startTimer()
    }
    }
    

def lockHandler(evt) {
    setTimeouts()
    state.lockStatus = evt.value   
    if(state.lockStatus == "unlocked"){
        LOGINFO("$lock1 unlocked so sending autolock command to lock in $state.delay1 seconds")
        runIn(state.delay1, lockIt)
    }
    if(state.lockStatus == "locked"){
    LOGINFO("Lock has reported that it is locked")
    }
}


def lockOnContact(){
    if(state.contactStatus == "closed" && state.timer == false){ 
        LOGINFO( "Contact closed so can now lock $lock1")
         lockIt()
    }
    else{ 
        LOGDEBUG("Checking every $state.delay2 seconds until contact closed")
         runIn(state.delay2, lockOnContact)
    }
}

def lockIt() {
    if(state.lockStatus == "locked"){ LOGDEBUG( "Lock already locked")} 
    else if(state.lockStatus == "unlocked" && state.contactStatus == "closed"){
        LOGDEBUG( "Sending lock command to lock")
        lock1.lock()
    state.open = false
    }
    else if(state.contactStatus == "open"){  
        LOGDEBUG( "Door open, so cannot lock - Will check again in another $state.delay2 seconds")
        state.open = true
       lockOnContact()
    }  
   
}	

def startTimer(){
  LOGINFO("Starting $state.delay2 minute timer")
  state.timer = true   
  runIn(state.delay2, resetTimer)  
}
def resetTimer(){
 state.timer = false  
    lockOnContact()
}


def LOGDEBUG(txt){
    try {
    	if (settings.debugmode) { log.debug("${app.label.replace(" ","_").toUpperCase()}  (Version ${state.version}) - ${txt}") }
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
}
def LOGINFO(txt){
    try {
    	if (settings.debugmode) { log.info("${app.label.replace(" ","_").toUpperCase()}  (Version ${state.version}) - ${txt}") }
    } catch(ex) {
    	log.error("LOGINFO unable to output requested data!")
    }
}


def setAppVersion(){
    state.version = "1.3.0"
}
