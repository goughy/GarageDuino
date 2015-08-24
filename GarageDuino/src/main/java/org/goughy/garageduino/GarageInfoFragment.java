package org.goughy.garageduino;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by goughy on 24/08/15.
 */
public class GarageInfoFragment extends Fragment
{
    private static final String ARG_SECTION_NUMBER = "section_number";

    private TextView infoText;

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static GarageInfoFragment newInstance( int sectionNumber )
    {
        GarageInfoFragment fragment = new GarageInfoFragment();
        Bundle args = new Bundle();
        args.putInt( ARG_SECTION_NUMBER, sectionNumber );
        fragment.setArguments( args );
        return fragment;
    }

    public GarageInfoFragment()
    {
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState )
    {
        View rootView = inflater.inflate( R.layout.fragment_info, container, false );
        return rootView;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        bindUi();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    private void bindUi()
    {
        if( infoText == null )
        {
            infoText = (TextView) getActivity().findViewById( R.id.garage_info );
            infoText.setText( "Current Info:\n" );
        }

    }


}
