
#include <RFduinoBLE.h>

#define DISCONNECTED 0
#define CONNECTED 1

#define CMD_TOGGLE 'T'

int switchMs = 500;

int GPIO2 = 2;
int _rssi = 0;
int state = DISCONNECTED;

char buffer[256];
int buflen = 0;

bool toggle = false;

void setup() 
{
  state = DISCONNECTED;
  buflen = 0;

  // output for Garage switch
  pinMode(GPIO2, OUTPUT);
  digitalWrite(GPIO2, LOW);
  
  RFduinoBLE.deviceName = "GarageDuino";
  RFduinoBLE.advertisementInterval = 1000;
  RFduinoBLE.txPowerLevel = +4; //limit range

//  Serial.begin(9600);
//  Serial.println("GarageDuino started");

  // start the BLE stack
  RFduinoBLE.begin();
}

void loop() 
{
// switch to lower power mode
//  RFduino_ULPDelay(SECONDS(1));
//  Serial.println("Post ULPDelay");

  while(RFduinoBLE.radioActive)
    ;
    
  if( toggle )
    toggleDoor();
}

void RFduinoBLE_onConnect()
{
  state = CONNECTED;
}

void RFduinoBLE_onDisconnect()
{
  state = DISCONNECTED;
}

void RFduinoBLE_onAdvertisement(bool start)
{
}

void RFduinoBLE_onRSSI(int rssi)
{
  _rssi = rssi;
}

void RFduinoBLE_onReceive(char *data, int len)
{
  if( len > 0 && data[0] == CMD_TOGGLE )
     toggle = true;
}

void displayBuffer()
{
  RFduinoBLE.sendInt(buflen);
  for( int i = 0; i < buflen; ++i )
  {
    RFduinoBLE.sendByte(state == CONNECTED ? 0x01 : 0x00);
  }
}

void toggleDoor()
{
    toggle = false;

    digitalWrite(GPIO2, LOW);
    delay(50);
    digitalWrite(GPIO2, HIGH);
    delay(switchMs);
    digitalWrite(GPIO2, LOW);
}

