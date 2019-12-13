/**
 *  Update URL: https://github.com/CobraVmax/Hubitat/blob/master/Apps/Average%20All/Average%20All%20Parent.groovy
 *
 *
 *  Copyright 2019 Andrew Parker
 *  
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you! -  @Cobra
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  Last Update: 03/12/2018
 *
 *  Changes:
 *
 *  V1.4.0 - Added disable app code
 *  V1.3.0 - Optimised restrictions checking to speed it up 
 *  V1.2.0 - Moved all app update checks to parent
 *  V1.1.1 - Debug & updated remote version checking
 *  V1.1.0 - Added to Cobra Apps as a child
 *  V1.0.2 - Revised auto update checking and added a manual update check button
 *  V1.0.1 - added revised first page
 *  V1.0.0 - POC
 *
 */

definition(
    name:"Average All",
    namespace: "smartport",
    author: "Andrew Parker",
    author: '{"authorName": "Andrew Parker", "smartportVersion": "1.4.0"}',
    description: "Parent App for Average All ChildApps ",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
    )

preferences {page name: "mainPage", title: "", install: true, uninstall: true}
def installed() {initialize()}
def updated() {initialize()}
def initialize() {
    version()
    log.debug "Initialised with settings: ${settings}"
    log.info "There are ${childApps.size()} child apps"
    childApps.each {child ->
    log.info "Child app: ${child.label}"
    }    
}

def mainPage() {
    dynamicPage(name: "mainPage") {   
	installCheck()
	updateCheck()
	if(state.appInstalled == 'COMPLETE'){
	display()

	section (){app(name: "averageApp", appName: "Average All Child", namespace: "Cobra", title: "<b>Add a new automation</b>", multiple: true)}
	displayDisable()
	}
  }
}

def version(){
//	unschedule(updateCheck)
    resetBtnName()
    schedule("0 0 9 ? * FRI *", updateCheck) //  Check for updates at 9am every Friday
    updateCheck()  
    checkButtons()
   
}


def installCheck(){         
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
	section{paragraph "Please hit 'Done' to load this app into the Cobra Apps container"}
	  }
	else{
 //      log.info "Parent Installed OK"  
    }
	}

def display(){
	if(state.status){section(){paragraph "<img src='http://update.hubitat.uk/icons/cobra3.png''</img> Version: $state.version <br><font face='Lucida Handwriting'>$state.Copyright </font>"}}
	if(state.status != "<b>** This app is no longer supported by $state.author  **</b>"){section(){input "updateBtn", "button", title: "$state.btnName"}}
	if(state.status != "Current"){
		section(){paragraph "<hr><b>Updated: </b><i>$state.Comment</i><br><br><i>Changes in version $state.newver</i><br>$state.UpdateInfo<hr><b>Update URL: </b><font color = 'red'> $state.updateURI</font><hr>"}
		}
		section(){
		input "updateNotification", "bool", title: "Send a 'Pushover' message when an update is available for either the parent or the child app", required: true, defaultValue: false, submitOnChange: true 
		if(updateNotification == true){ input "speakerUpdate", "capability.speechSynthesis", title: "PushOver Device", required: true, multiple: true}
		}
	
}

def displayDisable(){
	if(app.label){
	section("<hr>"){
		input "disableAll1", "bool", title: "Disable <b>all</b> <i>'${app.label}'</i> child apps", required: true, defaultValue: false, submitOnChange: true
		state.allDisabled1 = disableAll1
		stopAll()
	}
	section("<hr>"){}
	}
	else{
	section("<hr>"){
		input "disableAll1", "bool", title: "Disable <b><i>ALL</i></b> child apps ", required: true, defaultValue: false, submitOnChange: true
		state.allDisabled1 = disableAll1
		stopAll()
	}
	section("<hr>"){}
	}
	
}




def stopAll(){
	
	if(state.allDisabled1 == true) {
	log.debug "state.allDisabled1 = TRUE"
	state.msg2 = "Disabled by parent"
	childApps.each { child ->
	child.stopAllChildren(state.allDisabled1, state.msg2)
	log.warn "Disabling ChildApp: $child.label"
	}
	}	
	
	if(state.allDisabled1 == false){
	log.debug "state.allDisabled1 = FALSE"
	state.msg3 = "Enabled by parent"
	childApps.each { child ->
	child.stopAllChildren(state.allDisabled1, state.msg3)	
	log.trace "Enabling ChildApp: $child.label "
	}
	}
}

def stopAllParent(stopNowCobra, msgCobra){
	state.allDisabled1 = stopNowCobra
	def msgNowCobra = msgCobra
	log.info " Message from Cobra Apps -  Disable = $stopNowCobra"
	childApps.each { child ->
	child.stopAllChildren(state.allDisabled1, msgNowCobra)
	//	if(stopNowCobra == true){log.warn "Disabling ChildApp: $child.label"}
	//	if(stopNowCobra == false){log.trace "Enabling ChildApp: $child.label "}
		
		
		
	}
}	


def checkButtons(){
//    log.debug "Running checkButtons"
    appButtonHandler("updateBtn")
}


def appButtonHandler(btn){
	state.btnCall = btn
	if(state.btnCall == "updateBtn"){
        log.info "Checking for updates now..."
        updateCheck()
        pause(3000)
	state.btnName = state.newBtn
        runIn(2, resetBtnName)
    }
    
}  
 
def resetBtnName(){
//    log.info "Resetting Button"	
	if(state.status != "Current"){
	state.btnName = state.newBtn
	    }
	else{
 	state.btnName = "Check For Update" 
	}
}    
    


def updateCheck(){
    setVersion()
    def paramsUD = [uri: "http://update.hubitat.uk/json/${state.CobraAppCheck}"]
    
       	try {
        httpGet(paramsUD) { respUD ->
//  log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code 
       		def copyrightRead = (respUD.data.copyright)
       		state.Copyright = copyrightRead
            def commentRead = (respUD.data.Comment)
       		state.Comment = commentRead

            def updateUri = (respUD.data.versions.UpdateInfo.GithubFiles.(state.InternalName))
            state.updateURI = updateUri   
            
            def newVerRaw = (respUD.data.versions.Application.(state.InternalName))
            state.newver = newVerRaw
            def newVer = (respUD.data.versions.Application.(state.InternalName).replace(".", ""))
       		def currentVer = state.version.replace(".", "")
      		state.UpdateInfo = (respUD.data.versions.UpdateInfo.Application.(state.InternalName))
            state.author = (respUD.data.author)
           
		if(newVer == "NLS"){
            state.status = "<b>** This app is no longer supported by $state.author  **</b>"  
             log.warn "** This app is no longer supported by $state.author **" 
            
      		}           
		else if(currentVer < newVer){
        	state.status = "<b>New Version Available ($newVerRaw)</b>"
        	log.warn "** There is a newer version of this app available  (Version: $newVerRaw) **"
        	log.warn " Update: $state.UpdateInfo "
             state.newBtn = state.status
            state.updateMsg = "There is a new version of '$state.ExternalName' available (Version: $newVerRaw)"
            pushOverUpdate(state.updateMsg)
       		} 
		else{ 
      		state.status = "Current"
       		log.info("You are using the current version of this app")
       		}
      					}
        	} 
        catch (e) {
        	log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
    		}
    if(state.status != "Current"){
		state.newBtn = state.status
        
    }
    else{
        state.newBtn = "No Update Available"
    }
        
        
}

def childUpdate(set, msg){
	if(state.msgDone == false){
	state.childUpdate = set.value
	state.upMsg = msg.toString()
	if(state.childUpdate == true){
	pushOverUpdate(state.upMsg)	
	state.msgDone = true	
			}	
		}
	else{
//		log.info "Message already sent - Not able to send again today"
	    }		
}
def resetMsg(){state.msgDone = false}
def pushOverUpdate(inMsg){
    if(updateNotification == true){  
    newMessage = inMsg
   log.debug"PushOver Message = $newMessage "  
    state.msg1 = '[L]' + newMessage
    speakerUpdate.speak(state.msg1)
    }
}


def setVersion(){
		state.version = "1.4.0"	 
		state.InternalName = "AverageAllParent" 
    	state.ExternalName = "Average All Parent"
    	state.CobraAppCheck = "averageall.json"
}





