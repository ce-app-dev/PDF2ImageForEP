package com.artifex.mupdf;

import java.io.UnsupportedEncodingException;

import jp.co.muratec.pdf2image.LoadLibrary;
import jp.co.muratec.pdf2image.PDF2ImageActivity;
import jp.co.muratec.pdf2image.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;


class ProgressDialogX extends ProgressDialog {
	public ProgressDialogX(Context context) {
		super(context);
	}

	private boolean mCancelled = false;

	public boolean isCancelled() {
		return mCancelled;
	}

	@Override
	public void cancel() {
		mCancelled = true;
		super.cancel();
	}
}
public class MuPDFActivity extends Activity
{
	/* The core rendering instance */
	private enum LinkState {DEFAULT, HIGHLIGHT, INHIBIT};
	private final int    TAP_PAGE_MARGIN = 5;
	private static final int    SEARCH_PROGRESS_DELAY = 200;
	private MuPDFCore    core;
	private String       mFileName;
	private ReaderView   mDocView;
	private View         mButtonsView;
	private boolean      mButtonsVisible;
	private EditText     mPasswordView;
	private TextView     mFilenameView;
	//xujie debug
//	private SeekBar      mPageSlider;
	private int          mPageSliderRes;
	private TextView     mPageNumberView;
	private ImageButton  mSearchButton;
	private ImageButton  mCancelButton;
	private ImageButton  mOutlineButton;
	private ViewSwitcher mTopBarSwitcher;
// XXX	private ImageButton  mLinkButton;
	private boolean      mTopBarIsSearch;
	private ImageButton  mSearchBack;
	private ImageButton  mSearchFwd;
	private EditText     mSearchText;
	private SafeAsyncTask<Void,Integer,SearchTaskResult> mSearchTask;
	//private SearchTaskResult mSearchTaskResult;
	private AlertDialog.Builder mAlertBuilder;
	private LinkState    mLinkState = LinkState.DEFAULT;
	private final Handler mHandler = new Handler();
	//xujie debug
	private static boolean mIsFromMobilePrintPreview = false;
	public static final int RESULT_BACK = 1;
	private static final int ID_PDF_NAME = 0x01;
	private static final int ID_PAGES = 0x02;
	private static final int ID_PDF_PRINT = 0x03;
	private static final int ID_TOP_BAR = 0x04;
	private TextView     mPdfName;
	private TextView     mPdfPages;
	private ImageButton  mPrintButton;
	public static Activity mPreviewActivity;
	//xujie debug
	//luo
	private static boolean mIsMobilePhone = false;
	//luo

	private MuPDFCore openFile(String path)
	{
		int lastSlashPos = path.lastIndexOf('/');
		mFileName = new String(lastSlashPos == -1
					? path
					: path.substring(lastSlashPos+1));
		System.out.println("Trying to open "+path);
		try
		{
			core = new MuPDFCore(path);
			// New file: drop the old outline data
			OutlineActivityData.set(null);
		}
		catch (Exception e)
		{
			System.out.println(e);
			return null;
		}
		return core;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		// 他アプリからClassLoader経由で呼ばれる場合に備え、ライブラリ読み込みクラスを使うように変更したため、自アプリで使う時は事前準備が必要
		LoadLibrary instance = LoadLibrary.getInstance();
		try {
			instance.setAppPath(getPackageManager().getApplicationInfo("jp.co.muratec.pdf2image", 0).sourceDir , getFilesDir().getAbsolutePath());
		} catch (NameNotFoundException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		//xujie debug
		mPreviewActivity = this;
		mPdfName = new TextView(this);
		mPdfPages = new TextView(this);
		mPrintButton = new ImageButton(this);


		mAlertBuilder = new AlertDialog.Builder(this);

		if (core == null) {
			core = (MuPDFCore)getLastNonConfigurationInstance();

			if (savedInstanceState != null && savedInstanceState.containsKey("FileName")) {
				mFileName = savedInstanceState.getString("FileName");
			}
		}
		if (core == null) {
			Intent intent = getIntent();
			if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				Uri uri = intent.getData();
				//xujie debug
				mIsFromMobilePrintPreview = intent.getBooleanExtra("IS_FROM_MOBILEPRINT_PREVIEW", false);
				//xujie debug
				//luo
				mIsMobilePhone = intent.getBooleanExtra("IS_MOBILE_PHONE", false);
				//luo
				if (uri.toString().startsWith("content://media/external/file")) {
					// Handle view requests from the Transformer Prime's file manager
					// Hopefully other file managers will use this same scheme, if not
					// using explicit paths.
					Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
					if (cursor.moveToFirst()) {
						uri = Uri.parse(cursor.getString(0));
					}
				}
				//DVT不具合Ｎｏ.1001修正　by smuratec xujie
				String path = null;
				try {
					path = new String(uri.toString().getBytes("utf-8"));
				} catch (UnsupportedEncodingException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				}
				core = openFile(path);				
//				core = openFile(Uri.decode(uri.getEncodedPath()));
				//DVT不具合Ｎｏ.1001修正　by smuratec xujie
				
				SearchTaskResult.set(null);
			}
			if (core != null && core.needsPassword()) {
				requestPassword(savedInstanceState);
				return;
			}
		}
		if (core == null) {
			AlertDialog alert = mAlertBuilder.create();
			alert.setCancelable(false);
			alert.setTitle(R.string.open_failed);
			alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.close),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
					});
			alert.show();
			return;
		}

		createUI(savedInstanceState);
	}

	public void requestPassword(final Bundle savedInstanceState) {
		mPasswordView = new EditText(this);
		mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
		mPasswordView.setTransformationMethod(new PasswordTransformationMethod());

		AlertDialog alert = mAlertBuilder.create();
		alert.setCancelable(false);
		alert.setTitle(R.string.enter_password);
		alert.setView(mPasswordView);
		alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok),
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (core.authenticatePassword(mPasswordView.getText().toString())) {
					createUI(savedInstanceState);
				} else {
					requestPassword(savedInstanceState);
				}
			}
		});
		alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
				new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
		});
		alert.show();
	}

	public void createUI(Bundle savedInstanceState) {
		if (core == null)
			return;
    	// Now create the UI.
		// First create the document view making use of the ReaderView's internal
		// gesture recognition
		mDocView = new ReaderView(this) {
			private boolean showButtonsDisabled;

			public boolean onSingleTapUp(MotionEvent e) {
				if (e.getX() < super.getWidth()/TAP_PAGE_MARGIN) {
					super.moveToPrevious();
				} else if (e.getX() > super.getWidth()*(TAP_PAGE_MARGIN-1)/TAP_PAGE_MARGIN) {
					super.moveToNext();
				} else if (!showButtonsDisabled) {
					int linkPage = -1;
					if (mLinkState != LinkState.INHIBIT) {
						MuPDFPageView pageView = (MuPDFPageView) mDocView.getDisplayedView();
						if (pageView != null) {
// XXX							linkPage = pageView.hitLinkPage(e.getX(), e.getY());
						}
					}

					if (linkPage != -1) {
						mDocView.setDisplayedViewIndex(linkPage);
					} else {
						if (!mButtonsVisible) {
							showButtons();
						} else {
							hideButtons();
						}
					}
				}
				return super.onSingleTapUp(e);
			}

			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				if (!showButtonsDisabled)
					hideButtons();

				return super.onScroll(e1, e2, distanceX, distanceY);
			}

			public boolean onScaleBegin(ScaleGestureDetector d) {
				// Disabled showing the buttons until next touch.
				// Not sure why this is needed, but without it
				// pinch zoom can make the buttons appear
				showButtonsDisabled = true;
				return super.onScaleBegin(d);
			}

			public boolean onTouchEvent(MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
					//xujie debug
					if(mIsFromMobilePrintPreview == true){
						showButtonsDisabled = true;
					}
					else{
						showButtonsDisabled = false;
					}
				//xujie debug
//					showButtonsDisabled = false;

				return super.onTouchEvent(event);
			}

			protected void onChildSetup(int i, View v) {
				if (SearchTaskResult.get() != null && SearchTaskResult.get().pageNumber == i)
					((PageView)v).setSearchBoxes(SearchTaskResult.get().searchBoxes);
				else
					((PageView)v).setSearchBoxes(null);

				((PageView)v).setLinkHighlighting(mLinkState == LinkState.HIGHLIGHT);
			}

			protected void onMoveToChild(int i) {
				if (core == null)
					return;
				mPageNumberView.setText(String.format("%d/%d", i+1, core.countPages()));
				//xujie debug
				mPdfPages.setText(String.format("%d/%d", i+1, core.countPages()));
//				mPageSlider.setMax((core.countPages()-1) * mPageSliderRes);
//				mPageSlider.setProgress(i * mPageSliderRes);
				if (SearchTaskResult.get() != null && SearchTaskResult.get().pageNumber != i) {
					SearchTaskResult.set(null);
					mDocView.resetupChildren();
				}
			}

			protected void onSettle(View v) {
				// When the layout has settled ask the page to render
				// in HQ
				((PageView)v).addHq();
			}

			protected void onUnsettle(View v) {
				// When something changes making the previous settled view
				// no longer appropriate, tell the page to remove HQ
				((PageView)v).removeHq();
			}

			@Override
			protected void onNotInUse(View v) {
				((PageView)v).releaseResources();
			}
		};
		mDocView.setAdapter(new MuPDFPageAdapter(this, core));

		// Make the buttons overlay, and store all its
		// controls in variables
		makeButtonsView();

		// Set up the page slider
		int smax = Math.max(core.countPages()-1,1);
		mPageSliderRes = ((10 + smax - 1)/smax) * 2;

		// Set the file-name text
		mFilenameView.setText(mFileName);

		// Activate the seekbar
		//xujie debug
//		mPageSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//			public void onStopTrackingTouch(SeekBar seekBar) {
//				mDocView.setDisplayedViewIndex((seekBar.getProgress()+mPageSliderRes/2)/mPageSliderRes);
//			}
//
//			public void onStartTrackingTouch(SeekBar seekBar) {}
//
//			public void onProgressChanged(SeekBar seekBar, int progress,
//					boolean fromUser) {
//				updatePageNumView((progress+mPageSliderRes/2)/mPageSliderRes);
//			}
//		});

		// Activate the search-preparing button
//		mSearchButton.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				//xujie debug
//				if(mIsFromMobilePrintPreview == true){
//					int pdf_pages = 0;
//					pdf_pages = core.countPages();
////					Intent intent = new Intent();
////					intent.setClassName("jp.co.muratec.mobileprint", "jp.co.muratec.mobileprint.MobilePrintMenuActivity");
////					startActivity(intent);
//					Intent intent = new Intent();				
//					intent.putExtra("PDF_PAGES", pdf_pages);
//					setResult(RESULT_OK, intent);
//					finish();
//				}
//				else{
//					searchModeOn();
//				}
//				//xujie debug
////				searchModeOn();
//			}
//		});
		//xujie debug
		mPrintButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				MuPDFPageView pageView = (MuPDFPageView) mDocView.getDisplayedView();
				if (pageView != null) {
					if (pageView.isBusyIndicator()) {
						return;
					}
				}
				
//				int pdf_pages = 0;
//				pdf_pages = core.countPages();
//				Intent intent = new Intent();				
//				intent.putExtra("PDF_PAGES", pdf_pages);
//				setResult(RESULT_OK, intent);
				//luo
				if (mIsMobilePhone == false) {
					Intent intentPrintConfig = new Intent();
					intentPrintConfig.putExtra("MP_PRINT_CONFIG", 0); // MobilePrintMenuListViewFragment.MPMENU_DOCUMENT_PRINT
					intentPrintConfig.setAction("jp.co.muratec.mobileprint.response");
					startActivity(intentPrintConfig);
				}
				else {
					Intent intentPrintConfig = new Intent(); 
					intentPrintConfig.setAction("jp.co.muratec.print.response");  
					startActivity(intentPrintConfig);
				}
				//luo
				finish();

			}
		});

		mCancelButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				searchModeOff();
			}
		});

		// Search invoking buttons are disabled while there is no text specified
		mSearchBack.setEnabled(false);
		mSearchFwd.setEnabled(false);

		// React to interaction with the text widget
		mSearchText.addTextChangedListener(new TextWatcher() {

			public void afterTextChanged(Editable s) {
				boolean haveText = s.toString().length() > 0;
				mSearchBack.setEnabled(haveText);
				mSearchFwd.setEnabled(haveText);

				// Remove any previous search results
				if (SearchTaskResult.get() != null && !mSearchText.getText().toString().equals(SearchTaskResult.get().txt)) {
					SearchTaskResult.set(null);
					mDocView.resetupChildren();
				}
			}
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {}
		});

		//React to Done button on keyboard
		mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE)
					search(1);
				return false;
			}
		});

		mSearchText.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER)
					search(1);
				return false;
			}
		});

		// Activate search invoking buttons
		mSearchBack.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				search(-1);
			}
		});
		mSearchFwd.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				search(1);
			}
		});

/* XXX
		mLinkButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				switch(mLinkState) {
				case DEFAULT:
					mLinkState = LinkState.HIGHLIGHT;
					mLinkButton.setImageResource(R.drawable.ic_hl_link);
					//Inform pages of the change.
					mDocView.resetupChildren();
					break;
				case HIGHLIGHT:
					mLinkState = LinkState.INHIBIT;
					mLinkButton.setImageResource(R.drawable.ic_nolink);
					//Inform pages of the change.
					mDocView.resetupChildren();
					break;
				case INHIBIT:
					mLinkState = LinkState.DEFAULT;
					mLinkButton.setImageResource(R.drawable.ic_link);
					break;
				}
			}
		});
*/

		if (core.hasOutline()) {
			mOutlineButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					OutlineItem outline[] = core.getOutline();
					if (outline != null) {
						OutlineActivityData.get().items = outline;
						Intent intent = new Intent(MuPDFActivity.this, OutlineActivity.class);
						startActivityForResult(intent, 0);
					}
				}
			});
		} else {
			mOutlineButton.setVisibility(View.GONE);
		}

		// Reenstate last state if it was recorded
		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		mDocView.setDisplayedViewIndex(prefs.getInt("page"+mFileName, 0));

		if (savedInstanceState == null || !savedInstanceState.getBoolean("ButtonsHidden", false))
			showButtons();

		if(savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false))
			searchModeOn();

		// Stick the document view and the buttons overlay into a parent view
		RelativeLayout layout = new RelativeLayout(this);
		//xujie debug
		
		RelativeLayout topBarlayout = new RelativeLayout(this);
		topBarlayout.setBackgroundColor(0xFF222222);
		topBarlayout.setId(ID_TOP_BAR);
		mPrintButton.setId(ID_PDF_PRINT);
		mPrintButton.setBackgroundDrawable(this.getResources().getDrawable(R.drawable.ic_print));
		RelativeLayout.LayoutParams detailParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		detailParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		mPrintButton.setLayoutParams(detailParams);
		topBarlayout.addView(mPrintButton);
		

		mPdfName.setId(ID_PDF_NAME);
		mPdfName.setGravity(Gravity.CENTER_VERTICAL);
		mPdfName.setTextSize(20);
		mPdfName.setTextColor(Color.WHITE);
		detailParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		detailParams.addRule(RelativeLayout.ALIGN_BOTTOM,ID_PDF_PRINT);
		detailParams.addRule(RelativeLayout.ALIGN_TOP,ID_PDF_PRINT);
		detailParams.addRule(RelativeLayout.LEFT_OF, ID_PDF_PRINT);
		mPdfName.setLayoutParams(detailParams);        
		mPdfName.setText(mFileName);
		mPdfName.setEllipsize(TextUtils.TruncateAt.END);	// 文字がセルのサイズをはみ出す場合に'…'で省略できるようにする
		mPdfName.setSingleLine(true);						// 文字がセルのサイズをはみ出す場合に'…'で省略できるようにする
		mPdfName.setPadding(20, 0, 0, 0);
		mPdfName.setBackgroundResource(R.color.bar);
		topBarlayout.addView(mPdfName);
		layout.addView(topBarlayout);
		
        
        mPdfPages.setId(ID_PAGES);
        detailParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); 
        detailParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        mPdfPages.setLayoutParams(detailParams);        
        mPdfPages.setGravity(Gravity.CENTER_VERTICAL);
        mPdfPages.setTextSize(20);
        mPdfPages.setTextColor(Color.WHITE);        
        mPdfPages.setPadding(20, 0, 0, 0);
        mPdfPages.setBackgroundResource(R.color.bar);
        layout.addView(mPdfPages);	
 		
		RelativeLayout.LayoutParams detailParams1 = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);  	
    	detailParams.addRule(RelativeLayout.ABOVE, R.id.pageNumber);
    	detailParams1.addRule(RelativeLayout.BELOW, ID_TOP_BAR);
    	detailParams1.addRule(RelativeLayout.ABOVE, ID_PAGES);
    	mDocView.setLayoutParams(detailParams1);
//		layout.addView(mButtonsView);
		layout.addView(mDocView);
		layout.setBackgroundResource(R.drawable.tiled_background);
//		layout.setBackgroundResource(R.color.bar);
		setContentView(layout);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode >= 0)
			mDocView.setDisplayedViewIndex(resultCode);
		super.onActivityResult(requestCode, resultCode, data);
	}

	public Object onRetainNonConfigurationInstance()
	{
		MuPDFCore mycore = core;
		core = null;
		return mycore;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mFileName != null && mDocView != null) {
			outState.putString("FileName", mFileName);

			// Store current page in the prefs against the file name,
			// so that we can pick it up each time the file is loaded
			// Other info is needed only for screen-orientation change,
			// so it can go in the bundle
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putInt("page"+mFileName, mDocView.getDisplayedViewIndex());
			edit.commit();
		}

		if (!mButtonsVisible)
			outState.putBoolean("ButtonsHidden", true);

		if (mTopBarIsSearch)
			outState.putBoolean("SearchMode", true);
	}

	@Override
	protected void onPause() {
		super.onPause();

		killSearch();

		if (mFileName != null && mDocView != null) {
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putInt("page"+mFileName, mDocView.getDisplayedViewIndex());
			edit.commit();
		}
	}

	public void onDestroy()
	{
		if (core != null)
			core.onDestroy();
		core = null;
		super.onDestroy();
	}

	void showButtons() {
		if (core == null)
			return;
		if (!mButtonsVisible) {
			mButtonsVisible = true;
			// Update page number text and slider
			int index = mDocView.getDisplayedViewIndex();
			updatePageNumView(index);
			//xujie debug
//			mPageSlider.setMax((core.countPages()-1)*mPageSliderRes);
//			mPageSlider.setProgress(index*mPageSliderRes);
			if (mTopBarIsSearch) {
				mSearchText.requestFocus();
				showKeyboard();
			}

			Animation anim = new TranslateAnimation(0, 0, -mTopBarSwitcher.getHeight(), 0);
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					mTopBarSwitcher.setVisibility(View.VISIBLE);
				}
				public void onAnimationRepeat(Animation animation) {}
				public void onAnimationEnd(Animation animation) {}
			});
			mTopBarSwitcher.startAnimation(anim);

			//xujie debug
			mPageNumberView.setVisibility(View.VISIBLE);
//			anim = new TranslateAnimation(0, 0, mPageSlider.getHeight(), 0);
//			anim.setDuration(200);
//			anim.setAnimationListener(new Animation.AnimationListener() {
//				public void onAnimationStart(Animation animation) {
//					mPageSlider.setVisibility(View.VISIBLE);
//				}
//				public void onAnimationRepeat(Animation animation) {}
//				public void onAnimationEnd(Animation animation) {
//					mPageNumberView.setVisibility(View.VISIBLE);
//				}
//			});
//			mPageSlider.startAnimation(anim);
		}
	}

	void hideButtons() {
		if (mButtonsVisible) {
			mButtonsVisible = false;
			hideKeyboard();

			Animation anim = new TranslateAnimation(0, 0, 0, -mTopBarSwitcher.getHeight());
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {}
				public void onAnimationRepeat(Animation animation) {}
				public void onAnimationEnd(Animation animation) {
					mTopBarSwitcher.setVisibility(View.INVISIBLE);
				}
			});
			mTopBarSwitcher.startAnimation(anim);

			//xujie debug
			mPageNumberView.setVisibility(View.VISIBLE);
//			anim = new TranslateAnimation(0, 0, 0, mPageSlider.getHeight());
//			anim.setDuration(200);
//			anim.setAnimationListener(new Animation.AnimationListener() {
//				public void onAnimationStart(Animation animation) {
//					mPageNumberView.setVisibility(View.INVISIBLE);
//				}
//				public void onAnimationRepeat(Animation animation) {}
//				public void onAnimationEnd(Animation animation) {
//					mPageSlider.setVisibility(View.INVISIBLE);
//				}
//			});
//			mPageSlider.startAnimation(anim);
		}
	}

	void searchModeOn() {
		if (!mTopBarIsSearch) {
			mTopBarIsSearch = true;
			//Focus on EditTextWidget
			mSearchText.requestFocus();
			showKeyboard();
			mTopBarSwitcher.showNext();
		}
	}

	void searchModeOff() {
		if (mTopBarIsSearch) {
			mTopBarIsSearch = false;
			hideKeyboard();
			mTopBarSwitcher.showPrevious();
			SearchTaskResult.set(null);
			// Make the ReaderView act on the change to mSearchTaskResult
			// via overridden onChildSetup method.
			mDocView.resetupChildren();
		}
	}

	void updatePageNumView(int index) {
		if (core == null)
			return;
		mPageNumberView.setText(String.format("%d/%d", index+1, core.countPages()));
		mPdfPages.setText(String.format("%d/%d", index+1, core.countPages()));
	}

	void makeButtonsView() {
		mButtonsView = getLayoutInflater().inflate(R.layout.buttons,null);
		mFilenameView = (TextView)mButtonsView.findViewById(R.id.docNameText);
		//xujie debug
//		mPageSlider = (SeekBar)mButtonsView.findViewById(R.id.pageSlider);
		mPageNumberView = (TextView)mButtonsView.findViewById(R.id.pageNumber);
		mSearchButton = (ImageButton)mButtonsView.findViewById(R.id.searchButton);
		mCancelButton = (ImageButton)mButtonsView.findViewById(R.id.cancel);
		mOutlineButton = (ImageButton)mButtonsView.findViewById(R.id.outlineButton);
		mTopBarSwitcher = (ViewSwitcher)mButtonsView.findViewById(R.id.switcher);
		//xujie debug
		if(mIsFromMobilePrintPreview == true){
			mSearchButton.setBackgroundDrawable(this.getResources().getDrawable(R.drawable.ic_print));
		}
		else{
			mSearchButton.setBackgroundDrawable(this.getResources().getDrawable(R.drawable.ic_magnifying_glass));
		}
		
		//xujie debug
		mSearchBack = (ImageButton)mButtonsView.findViewById(R.id.searchBack);
		mSearchFwd = (ImageButton)mButtonsView.findViewById(R.id.searchForward);
		mSearchText = (EditText)mButtonsView.findViewById(R.id.searchText);
// XXX		mLinkButton = (ImageButton)mButtonsView.findViewById(R.id.linkButton);
		mTopBarSwitcher.setVisibility(View.INVISIBLE);
		mPageNumberView.setVisibility(View.INVISIBLE);
		//xujie debug
//		mPageSlider.setVisibility(View.INVISIBLE);
	}

	void showKeyboard() {
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.showSoftInput(mSearchText, 0);
	}

	void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
	}

	void killSearch() {
		if (mSearchTask != null) {
			mSearchTask.cancel(true);
			mSearchTask = null;
		}
	}

	void search(int direction) {
		hideKeyboard();
		if (core == null)
			return;
		killSearch();

		final int increment = direction;
		final int startIndex = SearchTaskResult.get() == null ? mDocView.getDisplayedViewIndex() : SearchTaskResult.get().pageNumber + increment;

		final ProgressDialogX progressDialog = new ProgressDialogX(this);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setTitle(getString(R.string.searching_));
		progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				killSearch();
			}
		});
		progressDialog.setMax(core.countPages());

		mSearchTask = new SafeAsyncTask<Void,Integer,SearchTaskResult>() {
			@Override
			protected SearchTaskResult doInBackground(Void... params) {
				int index = startIndex;

				while (0 <= index && index < core.countPages() && !isCancelled()) {
					publishProgress(index);
					RectF searchHits[] = core.searchPage(index, mSearchText.getText().toString());

					if (searchHits != null && searchHits.length > 0)
						return new SearchTaskResult(mSearchText.getText().toString(), index, searchHits);

					index += increment;
				}
				return null;
			}

			@Override
			protected void onPostExecute(SearchTaskResult result) {
				progressDialog.cancel();
				if (result != null) {
					// Ask the ReaderView to move to the resulting page
					mDocView.setDisplayedViewIndex(result.pageNumber);
				    SearchTaskResult.set(result);
					// Make the ReaderView act on the change to mSearchTaskResult
					// via overridden onChildSetup method.
				    mDocView.resetupChildren();
				} else {
					mAlertBuilder.setTitle(SearchTaskResult.get() == null ? R.string.text_not_found : R.string.no_further_occurences_found);
					AlertDialog alert = mAlertBuilder.create();
					alert.setCancelable(false);
					alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.close),
							(DialogInterface.OnClickListener)null);
					alert.show();
				}
			}

			@Override
			protected void onCancelled() {
				super.onCancelled();
				progressDialog.cancel();
			}

			@Override
			protected void onProgressUpdate(Integer... values) {
				super.onProgressUpdate(values);
				progressDialog.setProgress(values[0].intValue());
			}

			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				mHandler.postDelayed(new Runnable() {
					public void run() {
						if (!progressDialog.isCancelled())
						{
							progressDialog.show();
							progressDialog.setProgress(startIndex);
						}
					}
				}, SEARCH_PROGRESS_DELAY);
			}
		};

		mSearchTask.safeExecute();
	}

	@Override
	public boolean onSearchRequested() {
		if (mButtonsVisible && mTopBarIsSearch) {
			hideButtons();
		} else {
			showButtons();
			searchModeOn();
		}
		return super.onSearchRequested();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mButtonsVisible && !mTopBarIsSearch) {
			hideButtons();
		} else {
			showButtons();
			searchModeOff();
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onBackPressed() {
		if (mDocView != null) {
			MuPDFPageView pageView = (MuPDFPageView) mDocView.getDisplayedView();
			if (pageView != null) {
				if (pageView.isBusyIndicator()) {
					return;
				}
			}
		}
		
//xujie debug
//		if (mIsFromMobilePrintPreview) {
//			setResult(RESULT_BACK);		
//		}
//xujie debug
			
		super.onBackPressed();
	}
}
