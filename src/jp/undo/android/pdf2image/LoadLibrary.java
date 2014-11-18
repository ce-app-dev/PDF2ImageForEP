/* ネイティブライブラリを呼び出す
 * 通常は、static { System.loadLibrary("ライブラリ名"); }で良い（com.artifex.mupdf.MuPDFCore.java冒頭のコメント部分）
 * このアプリでは、別アプリからプラグイン形式としてClassLoader経由で呼び出される事を想定したため、ライブラリの呼び出し方法を変更した
 * 		ClassLoader経由で呼び出す場合、呼び出し側の指定パスにライブラリファイルをコピーして、コピーしたファイルを読む
 * 		アプリ間でファイルの共有を行うため、この方式を使うためには、Manifestにandroid:sharedUserIdを指定し、共通の署名を使用する必要がある
 */

package jp.undo.android.pdf2image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.os.Build;

public class LoadLibrary {
	// シングルトンパターン
	private static LoadLibrary instance = new LoadLibrary();
	private LoadLibrary() {}
	
	// インスタンス取得メソッド
	public static LoadLibrary getInstance() {
		return instance;
	}
	
	
	/*
	 * 以下、クラスの処理部
	 */
	private boolean initFlag = false;
	private String sourceDir = null;
	private String outDir    = null;
	
	// 初期化（自アプリから呼び出す版）
	public void setAppPath(String _sourceDir,String _outDir) {
		sourceDir = _sourceDir;
		outDir    = _outDir;
	}
	public void init(){
		init(sourceDir,outDir);
	}
	/**
	 * 初期化（他アプリからClassLoader経由で呼ばれた場合）
	 * @param appPath	<br>　本アプリの.apkのパス<br>　Context.getPackageManager().getApplicationInfo("本アプリのパッケージ名", 0).sourceDir<br>　で、取得して下さい
	 * @param outPath	<br>　.soファイルのコピー先<br>　呼び出し元と共有できるパスである事、ファイル名まで含むフルパスで指定
	 */
	public void init(String appPath,String outPath){
		if(initFlag == false){
			// 読み込みは１回のみとなるように注意！
			String soName = "libmupdf.so";
			if(Build.CPU_ABI.equals("armeabi-v7a")){
				soName = "libmupdf_neon.so";
			}
			Calendar calendar = Calendar.getInstance();
			int now   = calendar.get(Calendar.MILLISECOND);		// 同じファイル名だとSystem.loadした時にalready loadedが発生するので、頭に時間をつけてアクセス毎に別ファイルに
			File outDir = new File(outPath);
			File[] list = outDir.listFiles();
			for(File file : list){
				if(file.getName().endsWith(soName)){
					// 既にsoファイルが展開済みなら
					file.renameTo(new File(outPath,Integer.toString(now)+soName));
					System.load(outPath+"/"+Integer.toString(now)+soName);
					initFlag = true;
					return;
				}
			}
			// 本アプリ内のsoファイルをコピーして使う
			String copyFile = outPath+"/"+Integer.toString(now)+soName;
			ZipExtractFile(appPath,"assets/"+soName,copyFile);		// appPathにある.soファイルを、outPathにコピー
			System.load(copyFile);									// コピーしたライブラリを読み込む
			initFlag = true;
		}
	}
	  
	  //extract file from apk file
	  public static boolean ZipExtractFile(String ZipFile,String SrcFile,String DstFile) 
	  {
		  ZipFile zip;
		  try {
			  File DstFileObj=new File(DstFile);
			  zip = new ZipFile(ZipFile);
			  ZipEntry zipen = zip.getEntry(SrcFile);
			  if ((!DstFileObj.exists()) || (zipen.getSize()!=DstFileObj.length())) {
				  InputStream is = zip.getInputStream(zipen);
				  OutputStream os = new FileOutputStream(DstFile);
				  byte[] buf = new byte[8092];
				  int n;
				  while ((n = is.read(buf)) > 0) {
					  os.write(buf, 0, n);
				  }
				  os.close();
				  is.close();
			  }
		  } catch (IOException e) {
			  e.printStackTrace();
			  return false;
		  }
		  return true;
	  }
}
