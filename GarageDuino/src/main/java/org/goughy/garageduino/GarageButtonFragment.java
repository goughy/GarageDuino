package org.goughy.garageduino;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.UUID;

/**
 * Created by goughy on 24/08/15.
 */
public class GarageButtonFragment extends Fragment implements BluetoothAdapter.LeScanCallback
{
    private final static String TAG = "GarageButtonFragment";

    final private static int STATE_ERROR = 0;
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_BLUETOOTH_ENABLING = 2;
    final private static int STATE_DISCONNECTED = 3;
    final private static int STATE_SCANNING = 4;
    final private static int STATE_CONNECTING = 5;
    final private static int STATE_CONNECTED = 6;

    private String lastErr;
    private com.dd.CircularProgressButton garageBtn;
    private TextView label;

    private int state;
    private static final long SCAN_TIMEOUT= 8000;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;

    private RFduinoService rfduinoService;

    private Handler handler = new Handler();

    Runnable progressTimeout = new Runnable()
    {
        @Override
        public void run()
        {
            if( state == STATE_SCANNING )
                stopScanning();
        }
    };

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            int state = intent.getIntExtra( BluetoothAdapter.EXTRA_STATE, 0 );
            if( state == BluetoothAdapter.STATE_ON )
                updateState( STATE_DISCONNECTED );
            else if( state == BluetoothAdapter.STATE_OFF )
                updateState( STATE_BLUETOOTH_OFF );
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            state = bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE ? STATE_SCANNING : STATE_DISCONNECTED;
            updateUi();
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected( ComponentName name, IBinder service )
        {
            rfduinoService = ( (RFduinoService.LocalBinder) service ).getService();
            if( rfduinoService.initialize() )
            {
                if( rfduinoService.connect( bluetoothDevice.getAddress() ) )
                    updateState( STATE_CONNECTING );
            }
        }

        @Override
        public void onServiceDisconnected( ComponentName name )
        {
            rfduinoService = null;
            updateState( STATE_DISCONNECTED );
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            final String action = intent.getAction();
            if( RFduinoService.ACTION_CONNECTED.equals( action ) )
                updateState( STATE_CONNECTED );
            else if( RFduinoService.ACTION_DISCONNECTED.equals( action ) )
                updateState( STATE_DISCONNECTED );
//            else if( RFduinoService.ACTION_DATA_AVAILABLE.equals( action ) )
//                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
        }
    };



    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static GarageButtonFragment newInstance( int sectionNumber )
    {
        GarageButtonFragment fragment = new GarageButtonFragment();
        Bundle args = new Bundle();
        args.putInt( ARG_SECTION_NUMBER, sectionNumber );
        fragment.setArguments( args );
        return fragment;
    }

    public GarageButtonFragment()
    {
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState )
    {
        View rootView = inflater.inflate( R.layout.fragment_main, container, false );
        return rootView;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        getActivity().registerReceiver( scanModeReceiver, new IntentFilter( BluetoothAdapter.ACTION_SCAN_MODE_CHANGED ) );
        getActivity().registerReceiver( bluetoothStateReceiver, new IntentFilter( BluetoothAdapter.ACTION_STATE_CHANGED ) );
        getActivity().registerReceiver( rfduinoReceiver, RFduinoService.getIntentFilter() );

        bindUi();
        updateState( bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF );
        updateUi();
    }

    @Override
    public void onStop()
    {
        super.onStop();

        bluetoothAdapter.stopLeScan( this );

        getActivity().unregisterReceiver( scanModeReceiver );
        getActivity().unregisterReceiver( bluetoothStateReceiver );
        getActivity().unregisterReceiver( rfduinoReceiver );
    }

    private void bindUi()
    {
        if( garageBtn == null )
        {
            garageBtn = (com.dd.CircularProgressButton) getActivity().findViewById( R.id.garageBtn );
            garageBtn.setProgress( 0 );
            garageBtn.setOnClickListener( new View.OnClickListener()
            {
                @Override
                public void onClick( View v )
                {
                    buttonClicked();
                }
            } );
        }

        if( label == null )
        {
            label = (TextView) getActivity().findViewById( R.id.section_label );
            label.setText( "Disconnected" );
        }
    }

    private void updateState( int newState )
    {
        Log.i( TAG, "Changing state from " + state + " to " + newState );
        state = newState;
        updateUi();
    }

    private void updateUi()
    {
        switch( state )
        {
            case STATE_ERROR:
                garageBtn.setProgress( -1 );
                label.setText( lastErr );
                break;
            case STATE_BLUETOOTH_OFF:
                garageBtn.setProgress( 0 );
                garageBtn.setText( "Enable" );
                label.setText( "No bluetooth!" );
                break;
            case STATE_BLUETOOTH_ENABLING:
                label.setText( "Enabling bluetooth..." );
                break;
            case STATE_DISCONNECTED:
                garageBtn.setProgress( 0 );
                label.setText( lastErr == null ? "Disconnected" : lastErr );
                break;
            case STATE_SCANNING:
                label.setText( "Scanning..." );
                break;
            case STATE_CONNECTING:
                label.setText( "Connecting to " + bluetoothDevice.getName() + "..." );
                break;
            case STATE_CONNECTED:
                garageBtn.setProgress( 100 );
                label.setText( "" );
                break;
        }
    }

    private void buttonClicked()
    {
        switch( state )
        {
            case STATE_BLUETOOTH_OFF:
                enableBluetooth();
                break;
            case STATE_ERROR:
                updateState( bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF );
                garageBtn.setProgress( 0 );
                buttonClicked();
                break;
            case STATE_DISCONNECTED:
                startScanning();
                break;
            case STATE_CONNECTED:
                toggleDoor();
                break;
        }
    }

    @Override
    public void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        if( requestCode == 123 ) //enable bluetooth
        {
            if( resultCode == Activity.RESULT_OK )
            {
                if( bluetoothAdapter.isEnabled() )
                {
                    startScanning();
                    return;
                }
            }

            setError( "Bluetooth disabled" );
        }
    }

    private void enableBluetooth()
    {
        updateState( STATE_BLUETOOTH_ENABLING );

        Intent enableBT = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( enableBT, 123 );
    }

    private void startScanning()
    {
        updateState( STATE_SCANNING );

        bluetoothAdapter.startLeScan(
                new UUID[]{ RFduinoService.UUID_SERVICE },
                GarageButtonFragment.this );

        garageBtn.setIndeterminateProgressMode( true );
        garageBtn.setProgress( 50 );
        handler.postDelayed( progressTimeout, SCAN_TIMEOUT );
    }

    private void stopScanning()
    {
        bluetoothAdapter.stopLeScan( GarageButtonFragment.this );
        setError( "Timed out" );
    }

    private void toggleDoor()
    {
        if( rfduinoService != null && state == STATE_CONNECTED )
            rfduinoService.send( new byte[] { 'T' } );
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord)
    {
        bluetoothAdapter.stopLeScan( this );
        bluetoothDevice = device;

        Intent rfduinoIntent = new Intent(GarageButtonFragment.this.getActivity(), RFduinoService.class);
        getActivity().bindService( rfduinoIntent, rfduinoServiceConnection, Context.BIND_AUTO_CREATE );
    }

    private void setError( String msg )
    {
        lastErr = msg;
        updateState( STATE_ERROR );
    }


}
