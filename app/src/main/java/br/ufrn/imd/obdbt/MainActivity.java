package br.ufrn.imd.obdbt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.cardiomood.android.controls.gauge.BatteryIndicatorGauge;
import com.cardiomood.android.controls.gauge.SpeedometerGauge;
import com.cardiomood.android.controls.progress.CircularProgressBar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();

    public static final Integer REQUEST_ENABLE_BT = 1;

    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;

    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;

    AcceptThread mAcceptThread;
    ConnectedThread mConnectedThread;
    ExecuteThread mExecuteThread;

    String address = "AC:D1:B8:C0:A2:96";

    Button btnEnviarComando;
    EditText edtComando;
    TextView txtLog;
    TextView txtTemperatura;
    TextView txtRPM;
    TextView txtVelocidade;
    TextView txtBorboleta;
    TextView txtFluxoAr;

    SpeedometerGauge speedometer;
    BatteryIndicatorGauge batteryindicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
        btnEnviarComando = (Button) findViewById(R.id.btnEnviarComando);
        edtComando = (EditText) findViewById(R.id.edtComando);
        btnEnviarComando.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (edtComando.getText().toString().isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Informe o comando a ser enviado", Toast.LENGTH_SHORT).show();
                    return;
                }

                Log.i(TAG, "Enviando comando");
                String msg = edtComando.getText().toString() + "\r";
                mConnectedThread.write(msg.getBytes());
            }
        });
        */

        txtLog = (TextView) findViewById(R.id.txtLog);
        txtTemperatura = (TextView) findViewById(R.id.txtTemperatura);
        txtRPM = (TextView) findViewById(R.id.txtRPM);
        txtVelocidade = (TextView) findViewById(R.id.txtVelocidade);
        txtBorboleta = (TextView) findViewById(R.id.txtBorboleta);
        txtFluxoAr = (TextView) findViewById(R.id.txtFluxoAr);

        speedometer = (SpeedometerGauge) findViewById(R.id.speedometer);
        speedometer.setLabelConverter(new SpeedometerGauge.LabelConverter() {
            @Override
            public String getLabelFor(double progress, double maxProgress) {
                return String.valueOf((int) Math.round(progress));
            }
        });
        speedometer.setMaxSpeed(255);
        speedometer.setMajorTickStep(30);
        speedometer.setMinorTicks(2);
        speedometer.addColoredRange(0, 50, Color.GREEN);
        speedometer.addColoredRange(50, 110, Color.YELLOW);
        speedometer.addColoredRange(110, 255, Color.RED);


//        batteryindicator = (BatteryIndicatorGauge) findViewById(R.id.batteryindicator);
//        batteryindicator.setValue(80, 1000, 300);
//
//        CircularProgressBar circ = (CircularProgressBar) findViewById(R.id.circularprogress);
//        circ.setProgress(90, 1000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        configuraObd();
    }

    private void configuraObd() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(getApplicationContext(), "Bluetooth não suportado", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }


        Log.i(TAG, "Pegando dispositivo remoto");
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
        if (mBluetoothDevice == null) {
            Toast.makeText(getApplicationContext(), "Não foi possivel se conectar", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Log.i(TAG, "createRfcommSocketToServiceRecord");
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Erro ao criar createRfcommSocketToServiceRecord", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        try {
            Log.i(TAG, "mBluetoothSocket.connect");
            mBluetoothSocket.connect();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Erro ao conectar ao socket", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        manageConnectedSocket(mBluetoothSocket);
        mExecuteThread = new ExecuteThread();
        mExecuteThread.start();
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        Log.i(TAG, "manageConnectedSocket");
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        String msg = "AT E0\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT L0\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT ST 00\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT SP 0\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT Z\r";
        mConnectedThread.write(msg.getBytes());
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //Log.i(TAG, "handleMessage");
            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    //Toast.makeText(getApplicationContext(), "Entrou no handler: MESSAGE_READ:" + readMessage, Toast.LENGTH_SHORT).show();
                    addLog(readMessage);
                    Log.i(TAG, "readMessage:" + readMessage);
                    String[] linhas = readMessage.split("\\r\\n|\\r|\\n", -1);
                    for (int i = 0; i < linhas.length; i++) {
                        String linha = linhas[i];
                        String[] retorno = linha.trim().split(" ");
                        try {
                            if (retorno[0].trim().equals("41")) {
                                Log.i(TAG, "Retorno: " + retorno[0] + " " + retorno[1]);
                                if (retorno[1].trim().equals("05")) {
                                    int temp = Integer.parseInt(retorno[2].trim(), 16);
                                    temp = temp - 40;
                                    txtTemperatura.setText("Temperatura: " + temp + "Cº");
                                    Log.i(TAG, "Temperatura: " + temp + "Cº");
                                }
                                if (retorno[1].trim().equals("0C")) {
                                    int p1 = Integer.parseInt(retorno[2].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[3].trim(), 16);
                                    double rpm = (p1 * 256 + p2) / 4;
                                    txtRPM.setText("RPM: " + rpm);
                                    Log.i(TAG, "RPM: " + rpm);
                                }
                                if (retorno[1].trim().equals("0D")) {
                                    int vel = Integer.parseInt(retorno[2].trim(), 16);
                                    txtVelocidade.setText("Velocidade: " + vel + " km/h");
                                    Log.i(TAG, "Velocidade: " + vel + " km/h");
                                    speedometer.setSpeed(vel, 1000, 300);
                                }
                                if (retorno[1].trim().equals("11")) {
                                    int borb = Integer.parseInt(retorno[2].trim(), 16);
                                    borb = borb * 100 / 255;
                                    txtBorboleta.setText("Borboleta: " + borb + "%");
                                    Log.i(TAG, "Borboleta: " + borb + "%");
                                }
                                if (retorno[1].trim().equals("10")) {
                                    int p1 = Integer.parseInt(retorno[2].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[3].trim(), 16);
                                    double fluxo = (p1 * 256 + p2) / 100;
                                    txtFluxoAr.setText("Fluxo de Ar: " + fluxo);
                                    Log.i(TAG, "Fluxo de Ar: " + fluxo);
                                }
                            }
                            if (retorno[0].trim().equals("05") || retorno[0].trim().equals("0C") || retorno[0].trim().equals("0D") || retorno[0].trim().equals("11") || retorno[0].trim().equals("10")) {
                                Log.i(TAG, "Retorno2: " + retorno[0]);
                                if (retorno[0].trim().equals("05")) {
                                    int temp = Integer.parseInt(retorno[1].trim(), 16);
                                    temp = temp - 40;
                                    txtTemperatura.setText("Temperatura: " + temp + "Cº");
                                    Log.i(TAG, "Temperatura: " + temp + "Cº");
                                }
                                if (retorno[0].trim().equals("0C")) {
                                    int p1 = Integer.parseInt(retorno[1].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[2].trim(), 16);
                                    double rpm = (p1 * 256 + p2) / 4;
                                    txtRPM.setText("RPM: " + rpm);
                                    Log.i(TAG, "RPM: " + rpm);
                                }
                                if (retorno[0].trim().equals("0D")) {
                                    int vel = Integer.parseInt(retorno[1].trim(), 16);
                                    txtVelocidade.setText("Velocidade: " + vel + " km/h");
                                    Log.i(TAG, "Velocidade: " + vel + " km/h");
                                    speedometer.setSpeed(vel, 1000, 300);
                                }
                                if (retorno[0].trim().equals("11")) {
                                    int borb = Integer.parseInt(retorno[1].trim(), 16);
                                    borb = borb * 100 / 255;
                                    txtBorboleta.setText("Borboleta: " + borb + "%");
                                    Log.i(TAG, "Borboleta: " + borb + "%");
                                }
                                if (retorno[0].trim().equals("10")) {
                                    int p1 = Integer.parseInt(retorno[1].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[2].trim(), 16);
                                    double fluxo = (p1 * 256 + p2) / 100;
                                    txtFluxoAr.setText("Fluxo de Ar: " + fluxo);
                                    Log.i(TAG, "Fluxo de Ar: " + fluxo);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "erro readMessage:" + readMessage, e);
                        }
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    //Toast.makeText(getApplicationContext(), "Entrou no handler: MESSAGE_WRITE:" + writeMessage, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void addLog(String msg) {
        String texto = txtLog.getText().toString();
        StringBuilder builder = new StringBuilder();
        builder.append(texto + "\n");
        builder.append(msg);
        txtLog.setText(builder.toString());
    }


    private class ExecuteThread extends Thread {
        public void run() {
            while (true) {
                try {
                    String msg = "";
                    msg = "01 05\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 0C\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 0D\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 11\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 10\r";
                    mConnectedThread.write(msg.getBytes());

                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("obdbt", MY_UUID);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "Exception during read", e);
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            Log.i(TAG, "write");
            try {
                mmOutStream.write(bytes);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, bytes)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

}
