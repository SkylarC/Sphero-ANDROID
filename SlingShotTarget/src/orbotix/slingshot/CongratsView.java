package orbotix.slingshot;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by Orbotix Inc.
 * Date: 12/29/11
 *
 * @author Adam Williams
 */
public class CongratsView extends RelativeLayout {
    
    private ImageView mRays;
    private ImageView mStars;
    private TextView  mText;
    
    private Animation mTextAnim;
    private Animation mStarsAnim;
    private Animation mRaysAnim;
    
    public CongratsView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater li = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        li.inflate(R.layout.congrats_message, this);

        mRays  = (ImageView)findViewById(R.id.rays);
        mRaysAnim = AnimationUtils.loadAnimation(getContext(), R.anim.congrats_rays_spin);
        
        mStars = (ImageView)findViewById(R.id.stars);
        mStarsAnim = AnimationUtils.loadAnimation(getContext(), R.anim.congrats_stars_in);

        mText  = (TextView)findViewById(R.id.text);
        mTextAnim = AnimationUtils.loadAnimation(getContext(), R.anim.congrats_text_in);
    }

    /**
     * Show the CongratsView
     */
    public void show(){
        mText.setVisibility(VISIBLE);
        mStars.setVisibility(VISIBLE);
        mRays.setVisibility(VISIBLE);

        setBackgroundColor(0x66000000);
        
        mText.startAnimation(mTextAnim);
        
        mStars.startAnimation(mStarsAnim);

        mRays.startAnimation(mRaysAnim);
    }

    /**
     * Hide the CongratsView
     */
    public void hide(){
        mTextAnim.cancel();
        mTextAnim.reset();
        mText.setVisibility(INVISIBLE);

        mStarsAnim.cancel();
        mStarsAnim.reset();
        mStars.setVisibility(INVISIBLE);

        mRaysAnim.cancel();
        mRaysAnim.reset();
        mRays.setVisibility(INVISIBLE);

        setBackgroundColor(0x00000000);
    }
}
