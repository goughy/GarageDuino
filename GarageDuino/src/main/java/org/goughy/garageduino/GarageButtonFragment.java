package org.goughy.garageduino;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by goughy on 24/08/15.
 */
public class GarageButtonFragment extends Fragment
{
    public interface OnClickListener
    {
        public void onClick();
    }

    private final static String TAG = "GarageButtonFragment";

    private String lastErr;
    private com.dd.CircularProgressButton garageBtn;
    private TextView label;
    private State state = State.DISCONNECTED;
    private OnClickListener clickListener;

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

        bindUi();
        updateUi();

        Log.i( TAG, "Fragment started" );
    }

    @Override
    public void onStop()
    {
        super.onStop();
        Log.i( TAG, "Fragment stopped" );
    }

    @Override
    public void onAttach( Activity activity )
    {
        super.onAttach( activity );
        try
        {
            clickListener = (OnClickListener) activity;
        }
        catch( ClassCastException e )
        {
            throw new ClassCastException( activity.toString() + " must implement OnClickListener" );
        }
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
                    clickListener.onClick();
                }
            } );
        }

        if( label == null )
        {
            label = (TextView) getActivity().findViewById( R.id.section_label );
            label.setText( "Disconnected" );
        }
    }

    public void updateState( State newState )
    {
        Log.i( TAG, "Changing state from " + state + " to " + newState );
        state = newState;
        updateUi();
    }

    private void updateUi()
    {
        switch( state )
        {
            case ERROR:
                garageBtn.setProgress( -1 );
                label.setText( lastErr );
                break;
            case BLUETOOTH_OFF:
                garageBtn.setProgress( 0 );
                garageBtn.setText( "Enable" );
                label.setText( "No bluetooth!" );
                break;
            case BLUETOOTH_ENABLING:
                label.setText( "Enabling bluetooth..." );
                break;
            case DISCONNECTED:
                garageBtn.setProgress( 0 );
                label.setText( lastErr == null ? "Disconnected" : lastErr );
                break;
            case SCANNING:
                garageBtn.setIndeterminateProgressMode( true );
                garageBtn.setProgress( 50 );
                label.setText( "Scanning ..." );
                break;
            case CONNECTING:
                label.setText( "Connecting ..." );
                break;
            case CONNECTED:
                garageBtn.setProgress( 100 );
                label.setText( "" );
                break;
        }
    }

    public void setError( String msg )
    {
        lastErr = msg;
        updateState( State.ERROR );
    }
}
