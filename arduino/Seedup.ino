#include <Wire.h> //BH1750 IIC Mode 
#include <math.h> 
#include <String.h>

// Sensor Luz
int direccionBH1750 = 0x23; // Setea direccion i2c
byte lecturaBH1750[2];
uint16_t luz = 0;

// Sensor Humedad
int humedad = 0;

// Lampara
int pinLampara = 8;

// Riego
int pinRiego = 7;

int automatico = 1;
String lecturaBT;
int statusLuz = 0;
int statusRiego = 0;

String DESACTIVAR_MODO_AUTOMATICO = "auto-0";
String ACTIVAR_MODO_AUTOMATICO = "auto-1";
String APAGAR_LUZ = "luz-0";
String ENCENDER_LUZ = "luz-1";
String APAGAR_RIEGO = "riego-0";
String ENCENDER_RIEGO = "riego-1";

void setup()	
{
	// Inicializa el puerto serie que nos sirve para monitorear el estado del sistema
	Serial.begin(9600);

	// Inicializa el puerto que se comunica con el bluetooth
	Serial1.begin(9600);

	// Inicializa la librería Wire
	Wire.begin();
	
	// Inicializa la resolución del sensor de luz (Wire.begin())
	Wire.beginTransmission(direccionBH1750);
	Wire.write(0x10);
	Wire.endTransmission();

	// Inicializa como output los pins que controlan la lampara y el sistema de riego 
	pinMode(pinLampara, OUTPUT);
	pinMode(pinRiego, OUTPUT);
}	
	
void loop()	
{
	// Si hay datos en el puerto serie del bluetooth
	while(Serial1.available())
	{
		// Hago la lectura del string enviado
		lecturaBT = Serial1.readString();

		// El string puede contener más de un comando, debo parsearlo
		int maxIndex = lecturaBT.length() ;
		int strIndex[] = { 0, -1 };
		String comando;
		
		// Recorro caracter por caracter en búsqueda del delimitador "pipe"
		// que es el que me define cuando termina y comienza otro comando
		for( int i = 0; i < maxIndex; i++ ) {
			if (lecturaBT.charAt(i) == '|') {
				strIndex[0] = strIndex[1] + 1;
				strIndex[1] = i;
				
				// Comando enviado...
				comando = lecturaBT.substring( strIndex[0], strIndex[1] );
				Serial.println(comando);
				
				// Seteo el modo manual o automático
				if( comando == DESACTIVAR_MODO_AUTOMATICO )
					automatico = 0;
				else if( comando == ACTIVAR_MODO_AUTOMATICO)
					automatico = 1;
				
				// Si no está en modo automático, la app podría indicarme
				// si debo o no prender/apagar la luz o el sistema de riego
				if( automatico == 0 ) {
					if( comando == APAGAR_LUZ )
						apagarLuz();
						
					else if( comando == ENCENDER_LUZ )
						encenderLuz();
						
					else if( comando == APAGAR_RIEGO )
						apagarRiego();
						
					else if( comando == ENCENDER_RIEGO )
						encenderRiego();
				}
			}
		}
	}
	
	// Toma los datos de luz y humedad de los sensores
	luz = sensarLuz();
	humedad = sensarHumedad();
	
	// Imprime el estado en el puerto serial para monitorearlo
	Serial.print("Luminosidad: ");
	Serial.print(luz,DEC);		 
	Serial.print(" [lx]; "); 
	Serial.print("Humedad: ");
	Serial.print(humedad);
	Serial.print("; Automatico: ");
	Serial.println(automatico);
	
	// Si está en modo automático, determino si debo encender/apagar la luz o
	// el sistema de riego en base a los valores medidos por los sensores
	if( automatico == 1 ) {
	
		// Analizo el valor del sensor de luz
		if( luz >= 0 ) {
			if( luz < 15 )
				encenderLuz();
			else
				apagarLuz();
		}

		// Analizo el valor del sensor de humedad
		if( humedad < 30 )
			encenderRiego();
		else
			apagarRiego();
	}
	
	// Envío el estado del sistema embebido al bluetooth
	char JSON [100];
	sprintf(JSON,"{sensor-luz:%i,sensor-humedad:%i,actuador-luz:%i,actuador-riego:%i,automatico:%i}\r\n", luz, humedad, statusLuz, statusRiego, automatico);
	Serial1.write(JSON); 
	
	// Delay de 300ms
	delay( 300 );
}

// Toma los datos del sensor de luz
uint16_t sensarLuz() {

	// Inicia la transmisión, pide 2 bytes, los almacena y finaliza la transmisión
	int bytes_leidos = 0;
	Wire.requestFrom( direccionBH1750, 2 );
	while( Wire.available() ) {
		lecturaBH1750[i] = Wire.read();
		bytes_leidos++;
	}
	
	// Si leí correctamente los 2 bytes, interpreta la lectura y la devuelve
	// sino devuelve -1 indicando que no se pudo realizar la lectura 
	if( bytes_leidos == 2 )
		return ( ( lecturaBH1750[0] << 8 ) | lecturaBH1750[1] ) /1.2;
	else
		return -1;
}

// Enciende la luz y actualiza el estado
void encenderLuz() {
	digitalWrite( pinLampara, HIGH );
	statusLuz = 1;
}

// Apaga la luz y actualiza el estado
void apagarLuz() {
	digitalWrite( pinLampara, LOW );
	statusLuz = 0;
}

// Toma los datos del sensor de humedad
int sensarHumedad() {
	int lecturaAnalogica;
	int humedad;
	lecturaAnalogica = analogRead(A0);
	humedad = map(lecturaAnalogica, 0, 1023, 100, 0);
	return humedad;
}

// Enciende el sistema de riego y actualiza el estado
void encenderRiego() {
	digitalWrite( pinRiego, HIGH );
	statusRiego = 1;
}

// Apaga el sistema de riego y actualiza el estado
void apagarRiego() {
	digitalWrite( pinRiego, LOW );
	statusRiego = 0;
}
