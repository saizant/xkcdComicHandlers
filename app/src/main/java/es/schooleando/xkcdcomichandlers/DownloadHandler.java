package es.schooleando.xkcdcomichandlers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;

import static es.schooleando.xkcdcomichandlers.ComicManager.DOWNLOAD;
import static es.schooleando.xkcdcomichandlers.ComicManager.ERROR;
import static es.schooleando.xkcdcomichandlers.ComicManager.LOAD_IMAGE;
import static es.schooleando.xkcdcomichandlers.ComicManager.PROGRESS;

public class DownloadHandler extends Handler {

    //Referencias débiles para evitar Memory Leak
    private WeakReference<Context> contexto;
    private WeakReference<ComicManager.ImageHandler> imageHandler;

    public DownloadHandler(Looper looper, Context context, ComicManager.ImageHandler imageHandler) {
        super(looper);
        contexto = new WeakReference<>(context);
        this.imageHandler = new WeakReference<>(imageHandler);
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case(DOWNLOAD):
                       //nos descargará una imagen y una vez descargada enviaremos un mensaje LOAD_IMAGE al UI Thread indicando
                       //la URI del archivo descargado.
                       //También enviaremos mensajes PROGRESS al UI Thread indicando el porcentaje de progreso, si hay.
                       //Enviaremos mensajes ERROR, en caso de que haya un error en la conexión, descarga, etc...

                Message mensaje = imageHandler.get().obtainMessage();
                mensaje.what = ERROR;

                String urlString = (String)msg.obj;

                ConnectivityManager connectivityManager = (ConnectivityManager)contexto.get().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

                //Si está conectado...
                if (networkInfo != null && networkInfo.isConnected()) {
                    HttpURLConnection conexion = null;

                    try {
                        //Conexión
                        URL url = new URL(urlString);
                        conexion = (HttpURLConnection) url.openConnection();
                        StringBuilder resultado = new StringBuilder();
                        String cabecera = conexion.getHeaderField("Location");
                        if (cabecera != null) {
                            conexion = (HttpURLConnection) new URL(cabecera).openConnection();
                        }
                        conexion.connect();

                        //Comprobar respuesta del servidor
                        if (conexion.getResponseCode() == 200 || conexion.getResponseCode() == 201) {
                            //Flujos entrada
                            InputStream is = new BufferedInputStream(conexion.getInputStream());
                            BufferedReader br = new BufferedReader(new InputStreamReader(is));

                            //Se va leyendo y añadiendo
                            String linea;
                            while ((linea = br.readLine()) != null) {
                                resultado.append(linea);
                            }

                            //Desconexión
                            conexion.disconnect();

                            //Descargar el resultado JSON para leer la URL y descargar la imagen en carpeta temporal
                            JSONObject jsonObject = new JSONObject(resultado.toString());
                            String urlComic = jsonObject.getString("img");

                            url = new URL(urlComic);
                            conexion = (HttpURLConnection) url.openConnection();
                            conexion.setConnectTimeout(7000);
                            conexion.setReadTimeout(7000);
                            conexion.setRequestMethod("HEAD");
                            conexion.connect();

                            int tamanyo = conexion.getContentLength();
                            is = url.openStream();

                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];

                            //Escritura y progreso
                            for (int i; (i = is.read(buffer)) != -1; ) {
                                bos.write(buffer, 0, i);
                                Message mens = imageHandler.get().obtainMessage();
                                mens.what = PROGRESS;

                                if (tamanyo > 0) {
                                    mens.obj = bos.size() * 100 / tamanyo;
                                } else {
                                    mens.obj = i * -1;
                                }

                                imageHandler.get().sendMessage(mens);
                            }

                            //Temporal
                            File outputDir = contexto.get().getExternalCacheDir();
                            String[] datos = urlComic.split("/");
                            String[] trozo = datos[datos.length - 1].split("\\.");
                            File outputFile = File.createTempFile(trozo[0], "." + trozo[1], outputDir);
                            outputFile.deleteOnExit();      //Eliminar temporal al SALIR

                            //Flujos salida
                            FileOutputStream fos = new FileOutputStream(outputFile);
                            fos.write(bos.toByteArray());

                            mensaje.obj = outputFile.getPath() + "|" + jsonObject.getInt("num");
                            mensaje.what = LOAD_IMAGE;

                            //Cerrar flujos I/O
                            bos.close();
                            is.close();
                            fos.close();

                        } else {
                            mensaje.obj = "ERROR del código de respuesta del servidor: " + conexion.getResponseCode();
                        }

                    } catch (MalformedURLException e) {
                        mensaje.obj = "URL incorrecta. " + e.getMessage();
                    } catch (SocketTimeoutException e) {
                        mensaje.obj = "Excepción TimeOut. " + e.getMessage();
                    }catch (IOException e) {
                        mensaje.obj = "Error de I/O. " + e.getMessage();
                    } catch (JSONException e) {
                        mensaje.obj = "Error en el JSON. " + e.getMessage();
                    } catch (Exception e) {
                        mensaje.obj = "Excepción: " + e.getMessage();
                    } finally {
                        //Se comprueba siempre si la conexión sigue para desconectarla
                        if (conexion != null) {
                            conexion.disconnect();
                        }
                    }

                } else {
                    mensaje.obj = "No está conectado";
                }

                imageHandler.get().sendMessage(mensaje);

                break;

        }


        // No es necesario procesar Runnables luego no llamamos a super
        //super.handleMessage(msg);
    }
}
