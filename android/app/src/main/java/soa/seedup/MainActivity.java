package soa.seedup;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import org.json.JSONObject;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.BluetoothSPP.BluetoothConnectionListener;
import app.akexorcist.bluetotohspp.library.BluetoothSPP.OnDataReceivedListener;
import app.akexorcist.bluetotohspp.library.DeviceList;
import app.akexorcist.bluetotohspp.library.BluetoothSPP.BluetoothStateListener;

import static android.widget.CompoundButton.*;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView txtLuz, txtHumedad, txtTemperatura, txtEstado, txtLuzCelu;
    private Button btnConnect;
    private Switch switchLuz, switchRiego, switchModoManual;
    private BluetoothSPP bt;
    private final static int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1234;
    private boolean disableFeedback = false;
    private SensorManager mSensorManager;
    private Vibrator v;
    private final static float ACC = 30;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hace el binding de los controles a variables
        txtLuz = (TextView)findViewById(R.id.txtLuz);
        txtHumedad = (TextView)findViewById(R.id.txtHumedad);
        txtTemperatura = (TextView)findViewById(R.id.txtTemperatura);
        txtEstado = (TextView)findViewById(R.id.txtEstado);
        txtLuzCelu = (TextView)findViewById(R.id.txtLuzCelu);

        // Obtiene el servicio que da acceso a la info de los sensores
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Obtiene el servicio que da acceso al vibrator del celu
        v = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Instancia la clase de bluetooth
        bt = new BluetoothSPP(this);

        // Chequea que el dispositivo en el cual esta corriendo la app tenga bluetooth
        if(!bt.isBluetoothAvailable()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Este dispositivo no dispone de bluetooth por lo que no es posible ejecutar esta aplicación.");
            builder.setCancelable(false);
            builder.setPositiveButton(
                "Cerrar aplicacion",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
            AlertDialog alert = builder.create();
            alert.show();
        }
        else {

            // Listener para cuando se recibe info desde este adaptador bluetooth
            bt.setOnDataReceivedListener(new OnDataReceivedListener() {
                public void onDataReceived(byte[] data, String message) {

                    try
                    {
                        JSONObject jsonMessage = new JSONObject(message);

                        if( jsonMessage.has("sensor-luz") ) {
                            String text = jsonMessage.getString("sensor-luz") + " lux";
                            txtLuz.setText( text );
                        }

                        if( jsonMessage.has("sensor-humedad") ) {
                            String text = jsonMessage.getString("sensor-humedad") + "%";
                            txtHumedad.setText( text );
                        }

                        if( jsonMessage.has("sensor-temperatura") ) {
                            String text = jsonMessage.getString("sensor-temperatura") + "C";
                            txtTemperatura.setText( text );
                        }

                        if( jsonMessage.has("automatico") ) {
                            disableFeedback = true;
                            switchModoManual.setChecked( jsonMessage.getString("automatico").equals("0") );
                            disableFeedback = false;
                        }

                        if( jsonMessage.has("actuador-luz") ) {
                            disableFeedback = true;
                            switchLuz.setChecked( jsonMessage.getString("actuador-luz").equals("1") );
                            disableFeedback = false;
                        }

                        if( jsonMessage.has("actuador-riego") ) {
                            disableFeedback = true;
                            switchRiego.setChecked( jsonMessage.getString("actuador-riego").equals("1") );
                            disableFeedback = false;
                        }
                    }
                    catch (Exception e){
                        Toast.makeText(getApplicationContext(), "Problema al parsear JSON", Toast.LENGTH_SHORT).show();
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Listeners para cuando la conexion bluetooth cambia de estado
            bt.setBluetoothConnectionListener(new BluetoothConnectionListener() {
                public void onDeviceConnected(String name, String address) {
                    Toast.makeText(getApplicationContext(), "Conectado", Toast.LENGTH_SHORT).show();
                }
                public void onDeviceDisconnected() {
                    Toast.makeText(getApplicationContext(), "Desconectado", Toast.LENGTH_SHORT).show();
                }
                public void onDeviceConnectionFailed() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("No fue posible realizar la conexión con el dispositivo seleccionado. Compruebe que se encuentre encendido e intente nuevamente.");
                    builder.setCancelable( false );
                    builder.setPositiveButton( "Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();
                }
            });

            // Listeners para cuando el dispositivo bluetooth cambia de estado
            bt.setBluetoothStateListener(new BluetoothStateListener() {
                public void onServiceStateChanged(int state) {
                    switch( state ){
                        case BluetoothState.STATE_CONNECTING:
                            btnConnect.setEnabled( false );
                            btnConnect.setText( R.string.estado_conectando );
                            Toast.makeText(getApplicationContext(), "Conectando...", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothState.STATE_CONNECTED:
                            btnConnect.setEnabled( true );
                            switchModoManual.setEnabled( true );
                            txtEstado.setText( R.string.estado_conectado );
                            txtEstado.setTextColor( Color.rgb( 0x66, 0x99, 0x00 ) );
                            btnConnect.setText(R.string.boton_desconectar);
                            break;
                        default:
                            btnConnect.setEnabled( true );
                            switchModoManual.setEnabled( false );
                            switchLuz.setEnabled( false );
                            switchRiego.setEnabled( false );
                            txtHumedad.setText( R.string.valor_sin_dato );
                            txtLuz.setText( R.string.valor_sin_dato );
                            txtLuzCelu.setText( R.string.valor_sin_dato );
                            txtTemperatura.setText( R.string.valor_sin_dato );
                            txtEstado.setText( R.string.estado_desconectado );
                            txtEstado.setTextColor( Color.rgb( 0xCC, 0x00, 0x00 ) );
                            btnConnect.setText( R.string.boton_conectar );
                    }
                }
            });

            // Boton para conectarse/desconectarse
            btnConnect = (Button) findViewById(R.id.btnConnect);
            btnConnect.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    if(bt.getServiceState() == BluetoothState.STATE_CONNECTED) {
                        bt.disconnect();
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            switch (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION)) {
                                case PackageManager.PERMISSION_DENIED:
                                    openWarningPopup();
                                    break;
                                case PackageManager.PERMISSION_GRANTED:
                                    openDeviceList();
                                    break;
                            }
                        }
                        else {
                            openDeviceList();
                        }
                    }
                }
            });

            // Binding para el switch de luz
            switchLuz = (Switch) findViewById(R.id.switchLuz);
            switchLuz.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!disableFeedback)
                        sendToBluetooth(isChecked ? "luz-1" : "luz-0");
                }
            });

            // Binding para el switch de riego
            switchRiego = (Switch)findViewById(R.id.switchRiego);
            switchRiego.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!disableFeedback)
                        sendToBluetooth(isChecked ? "riego-1" : "riego-0");
                }
            });

            // Binding para el switch del modo
            switchModoManual = (Switch)findViewById(R.id.switchModo);
            switchModoManual.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!disableFeedback)
                        sendToBluetooth( isChecked ? "auto-0" : "auto-1");
                    switchLuz.setEnabled( isChecked );
                    switchRiego.setEnabled( isChecked );

                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        bt.stopService();
        unregisterSensors();
        System.exit(0);
    }

    @Override
    public void onStart() {
        super.onStart();
        if(bt.isBluetoothAvailable()) {
            if (!bt.isBluetoothEnabled()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, BluetoothState.REQUEST_ENABLE_BT);
            } else {
                if (!bt.isServiceAvailable()) {
                    bt.setupService();
                    bt.startService(BluetoothState.DEVICE_OTHER);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensors();
    }

    @Override
    protected void onStop() {
        unregisterSensors();
        super.onStop();
    }

    @Override
    protected void onPause() {
        unregisterSensors();
        super.onPause();
    }

    public void sendToBluetooth( String mensaje ) {
        if( bt.isBluetoothAvailable() ) {
            if( bt.isBluetoothEnabled() ) {
                if( bt.getServiceState() == BluetoothState.STATE_CONNECTED )
                    bt.send( mensaje + "|", false );
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch( requestCode ) {
            case BluetoothState.REQUEST_CONNECT_DEVICE:
                if(resultCode == Activity.RESULT_OK) {
                    bt.connect(data);
                }
                break;

            case BluetoothState.REQUEST_ENABLE_BT:
                if(resultCode == Activity.RESULT_OK) {
                    bt.setupService();
                    bt.startService(BluetoothState.DEVICE_OTHER);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("No es posible utilizar esta aplicación sin el bluetooth activado. Para poder continar debe habilitar el bluetooth.");
                    builder.setCancelable(false);
                    builder.setPositiveButton(
                            "Continuar",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                    startActivityForResult(intent, BluetoothState.REQUEST_ENABLE_BT);
                                }
                            });
                    AlertDialog alert = builder.create();
                    alert.show();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults ) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION: {
                if( grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
                    openDeviceList();
                } else {
                    openWarningPopup();
                }
            }
        }
    }

    public void openWarningPopup() {
        ((TextView) new AlertDialog.Builder(MainActivity.this)
                .setTitle("Petición de permisos")

                .setMessage("Para poder encontrar dispositivos bluetooth cercanos haz click en \"Permitir\" en la siguiente ventana de de petición de permisos")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
                        }
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show()
                .findViewById(android.R.id.message))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    public void openDeviceList() {
        Intent intent = new Intent(getApplicationContext(), DeviceList.class);
        startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values;
        synchronized (this) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    if ((Math.abs(values[0]) > ACC || Math.abs(values[1]) > ACC || Math.abs(values[2]) > ACC)) {
                        if( switchModoManual.isChecked() ) {
                            switchLuz.toggle();
                            v.vibrate(400);
                        }
                    }
                    break;
                case Sensor.TYPE_LIGHT:
                    if( txtEstado.getText() == getResources().getString(R.string.estado_conectado) ) {
                        String txt = event.values[0] + " lux";
                        txtLuzCelu.setText(txt);
                    }
                    break;
            }
        }
    }


    private void registerSensors() {
        boolean done;
        done = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        if( !done ) Toast.makeText(this, getResources().getString(R.string.sensor_no_disponible), Toast.LENGTH_SHORT).show();
        done = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_NORMAL);
        if( !done ) Toast.makeText(this, getResources().getString(R.string.sensor_no_disponible), Toast.LENGTH_SHORT).show();
    }

    private void unregisterSensors() {
        mSensorManager.unregisterListener(this);
    }

}
