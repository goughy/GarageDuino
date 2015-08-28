package org.goughy.garageduino;

import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import java.util.Locale;
import java.util.UUID;


public class RFduinoActivity extends Activity implements ActionBar.TabListener,
        BluetoothAdapter.LeScanCallback, GarageButtonFragment.OnClickListener
{
    private static final String TAG = "RFduinoActivity";

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v13.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private GarageButtonFragment buttonFragment;
    private GarageInfoFragment infoFragment;


    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private RFduinoService rfduinoService;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            int state = intent.getIntExtra( BluetoothAdapter.EXTRA_STATE, 0 );
            if( state == BluetoothAdapter.STATE_ON )
                updateState( State.DISCONNECTED );
            else if( state == BluetoothAdapter.STATE_OFF )
                updateState( State.BLUETOOTH_OFF );
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            state = bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE ? State.SCANNING : State.DISCONNECTED;

            if( bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_NONE )
                Log.i( TAG, "Scan receiver got BluetoothAdapter.SCAN_MODE_NONE" );
            else if( bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE )
                Log.i( TAG, "Scan receiver got BluetoothAdapter.SCAN_MODE_CONNECTABLE" );
            else if( bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE )
                Log.i( TAG, "Scan receiver got BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE" );
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
                {
                    updateState( State.CONNECTING );
                    Log.w( TAG, "rfduinoService connecting to " + bluetoothDevice.getAddress() );
                }
                else
                    Log.w( TAG, "rfduinoService failed to connect to " + bluetoothDevice.getAddress());
            }
            else
                Log.w( TAG, "rfduinoService failed to initialise!");
        }

        @Override
        public void onServiceDisconnected( ComponentName name )
        {
            rfduinoService = null;
            updateState( State.DISCONNECTED );
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            final String action = intent.getAction();
            if( RFduinoService.ACTION_CONNECTED.equals( action ) )
            {
                updateState( State.CONNECTED );
                Log.i( TAG, "Woohoo! Connected to " + bluetoothDevice.getAddress() );
            }
            else if( RFduinoService.ACTION_DISCONNECTED.equals( action ) )
            {
                updateState( State.DISCONNECTED );
                Log.i( TAG, "Hmmm! Sadly we've disconnected from " + bluetoothDevice.getAddress() );
            }
//            else if( RFduinoService.ACTION_DATA_AVAILABLE.equals( action ) )
//                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
        }
    };

    private State state;
    private static final long SCAN_TIMEOUT= 10000;

    private Handler handler = new Handler();

    Runnable progressTimeout = new Runnable()
    {
        @Override
        public void run()
        {
            if( state == State.SCANNING )
                stopScanning();
        }
    };

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode( ActionBar.NAVIGATION_MODE_TABS );

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter( getFragmentManager() );

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById( R.id.pager );
        mViewPager.setAdapter( mSectionsPagerAdapter );

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener( new ViewPager.SimpleOnPageChangeListener()
        {
            @Override
            public void onPageSelected( int position )
            {
                actionBar.setSelectedNavigationItem( position );
            }
        } );

        // For each of the sections in the app, add a tab to the action bar.
        for( int i = 0; i < mSectionsPagerAdapter.getCount(); i++ )
        {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText( mSectionsPagerAdapter.getPageTitle( i ) )
                            .setTabListener( this ) );
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        registerReceiver( scanModeReceiver, new IntentFilter( BluetoothAdapter.ACTION_SCAN_MODE_CHANGED ) );
        registerReceiver( bluetoothStateReceiver, new IntentFilter( BluetoothAdapter.ACTION_STATE_CHANGED ) );
        registerReceiver( rfduinoReceiver, RFduinoService.getIntentFilter() );

        updateState( bluetoothAdapter.isEnabled() ? State.DISCONNECTED : State.BLUETOOTH_OFF );
        Log.i(TAG, "Created");
    }

    @Override
    public void onDestroy()
    {
        if( state == State.SCANNING )
            bluetoothAdapter.stopLeScan( this );

        if( rfduinoService != null )
        {
            rfduinoService.disconnect();
            rfduinoService.close();
        }

        unregisterReceiver( scanModeReceiver );
        unregisterReceiver( bluetoothStateReceiver );
        unregisterReceiver( rfduinoReceiver );

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate( R.menu.menu_main, menu );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if( id == R.id.action_settings )
            return true;

        return super.onOptionsItemSelected( item );
    }

    @Override
    public void onTabSelected( ActionBar.Tab tab, FragmentTransaction fragmentTransaction )
    {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem( tab.getPosition() );
    }

    @Override
    public void onTabUnselected( ActionBar.Tab tab, FragmentTransaction fragmentTransaction )
    {
    }

    @Override
    public void onTabReselected( ActionBar.Tab tab, FragmentTransaction fragmentTransaction )
    {
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter
    {
        public SectionsPagerAdapter( FragmentManager fm )
        {
            super( fm );
        }

        @Override
        public Fragment getItem( int position )
        {
            if( position == 0 )
            {
                buttonFragment = GarageButtonFragment.newInstance( position + 1 );
                return buttonFragment;
            }
            else
            {
                infoFragment = GarageInfoFragment.newInstance( position + 1 );
                return infoFragment;
            }
        }

        @Override
        public int getCount()
        {
            // Show 3 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle( int position )
        {
            Locale l = Locale.getDefault();
            switch( position )
            {
                case 0:
                    return getString( R.string.title_section1 ).toUpperCase( l );
                case 1:
                    return getString( R.string.title_section2 ).toUpperCase( l );
            }
            return null;
        }
    }

    private void updateState( State newState )
    {
        Log.i( TAG, "Changing state from " + state + " to " + newState );
        state = newState;
        if( buttonFragment != null )
            buttonFragment.updateState( newState );
    }

    public void setError( String msg )
    {
        updateState( State.ERROR );
        if( buttonFragment != null )
            buttonFragment.setError( msg );
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord)
    {
        bluetoothAdapter.stopLeScan( this );
        bluetoothDevice = device;

        Log.i( TAG, "Found device " + device.getName() + " (" + device.getAddress() + ")" );

        startConnecting();
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
        updateState( State.BLUETOOTH_ENABLING );
        startActivityForResult( new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE ), 123 );
    }

    private void startScanning()
    {
        updateState( State.SCANNING );

        bluetoothAdapter.startLeScan(
                new UUID[]{ RFduinoService.UUID_SERVICE },
                this );

        handler.postDelayed( progressTimeout, SCAN_TIMEOUT );
        Log.i( TAG, "BTLE scanning" );
    }

    private void stopScanning()
    {
        bluetoothAdapter.stopLeScan( this );
        setError( "Timed out" );
    }

    private void startConnecting()
    {
        Intent rfduinoIntent = new Intent(this, RFduinoService.class);
        bindService( rfduinoIntent, rfduinoServiceConnection, Context.BIND_AUTO_CREATE );
    }

    private void toggleDoor()
    {
        if( rfduinoService != null )
            rfduinoService.send( new byte[] { 'T' } );
    }

    @Override
    public void onClick()
    {
        Log.i( TAG, "onClick() with state = " + state.name() );
        switch( state )
        {
            case BLUETOOTH_OFF:
                enableBluetooth();
                break;
            case ERROR:
                updateState( bluetoothAdapter.isEnabled() ? State.DISCONNECTED : State.BLUETOOTH_OFF );
                onClick();
                break;
            case DISCONNECTED:
                startScanning();
                break;
            case CONNECTED:
                toggleDoor();
                break;
        }
    }

}
