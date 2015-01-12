package net.androidsensei.socialsensei;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.facebook.FacebookRequestError;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.RequestBatch;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class LoginFacebookFragment extends Fragment {

    private static final String TAG = LoginFacebookFragment.class.getSimpleName();

    private UiLifecycleHelper uiHelper;

    private TextView mSaludo;

    private ProgressDialog progressDialog;

    private static final List<String> PERMISSIONS = Arrays.asList("publish_actions");

    private static final String PENDING_PUBLISH_KEY = "pendingPublishReauthorization";

    private boolean pendingPublishReauthorization = false;

    private Button shareButton;

    public LoginFacebookFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiHelper = new UiLifecycleHelper(getActivity(), callback);
        uiHelper.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        LoginButton authButton = (LoginButton) rootView.findViewById(R.id.login_button);
        mSaludo = (TextView)rootView.findViewById(R.id.txtSaludo);
        authButton.setFragment(this);
        authButton.setReadPermissions(Arrays.asList("public_profile","email"));

        shareButton = (Button)rootView.findViewById(R.id.compartir_button);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                publishStory();
            }
        });

        if (savedInstanceState != null) {
            pendingPublishReauthorization =
                    savedInstanceState.getBoolean(PENDING_PUBLISH_KEY, false);
        }

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Session session = Session.getActiveSession();

        if (session != null &&
                (session.isOpened() || session.isClosed()) ) {
            onSessionStateChange(session, session.getState(), null);
        }

        uiHelper.onResume();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        uiHelper.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
        uiHelper.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        uiHelper.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PENDING_PUBLISH_KEY, pendingPublishReauthorization);
        uiHelper.onSaveInstanceState(outState);
    }

    private void onSessionStateChange(Session session, SessionState state,
                                      Exception exception) {
        if (state.isOpened()) {
            Request.newMeRequest(session, new Request.GraphUserCallback() {
                @Override
                public void onCompleted(GraphUser user, Response response) {
                    if (user != null) {
                        mSaludo.setText("Bienvenido " + user.getName() + "!");
                    }
                }
            }).executeAsync();
            shareButton.setVisibility(View.VISIBLE);
            if (pendingPublishReauthorization &&
                    state.equals(SessionState.OPENED_TOKEN_UPDATED)) {
                pendingPublishReauthorization = false;
                publishStory();
            }
        } else if (state.isClosed()) {
            mSaludo.setText("Bienvenido");
            shareButton.setVisibility(View.INVISIBLE);
        }
    }

    private Session.StatusCallback callback = new Session.StatusCallback() {
        @Override
        public void call(Session session, SessionState state,
                         Exception exception) {
            onSessionStateChange(session, state, exception);
        }
    };

    private void publishStory() {

        Session session = Session.getActiveSession();
        if (session != null) {
            // Verifico si estan los permisos para publicar
            List<String> permissions = session.getPermissions();
            if (!isSubsetOf(PERMISSIONS, permissions)) {
                pendingPublishReauthorization = true;
                Session.NewPermissionsRequest newPermissionsRequest = new Session
                        .NewPermissionsRequest(this, PERMISSIONS);
                session.requestNewPublishPermissions(newPermissionsRequest);
                return;
            }

            // Muestro un progressDialog, ya que el publicar algo puede tomarse un tiempo
            progressDialog = ProgressDialog.show(getActivity(), "",
                    getActivity().getResources().getString(R.string.loading_msj), true);

            try {
                // Crea un Batch Request, primero creamos la imagen a compartir junto con el video
                //luego el objeto y por ultimo la accion referenciando al objecto recien creado.
                //por medio de su ID.
                RequestBatch requestBatch = new RequestBatch();

                // Configuro la imagen a subir
                Bundle imageParams = new Bundle();
                Bitmap image = BitmapFactory.decodeResource(this.getResources(),
                            R.drawable.dross);
                imageParams.putParcelable("file", image);

                // Configuro el callback que procesara la respuesta de la subida de la imagen
                Request.Callback imageCallback = new Request.Callback() {

                        @Override
                        public void onCompleted(Response response) {
                            // Si hay un error lo mostramos en el logcat
                            FacebookRequestError error = response.getError();
                            if (error != null) {
                                dismissProgressDialog();
                                Log.i(TAG, error.getErrorMessage());
                            }
                        }
                };

                // Request image upload
                Request imageRequest = new Request(Session.getActiveSession(),
                            "me/staging_resources", imageParams,
                            HttpMethod.POST, imageCallback);

                // Este nombre lo usamos luego en la creacion del objeto
                imageRequest.setBatchEntryName("imageUpload");

                // Agregamos el request para ser procesado
                requestBatch.add(imageRequest);

                //Creamos el objeto video que sera compartido
                JSONObject video = new JSONObject();
                video.put("image", "{result=imageUpload:$.uri}");
                video.put("title", "Las grabaciones de fantasmas más perturbadoras");
                video.put("type", "video.other");
                video.put("url", "https://www.youtube.com/watch?v=uY2rJYxCNAI");
                video.put("description", "Nuevo Video de Dross. Las grabaciones de fantasmas más perturbadoras");

                Bundle objectParams = new Bundle();
                objectParams.putString("object", video.toString());

                Request.Callback objectCallback = new Request.Callback() {

                    @Override
                    public void onCompleted(Response response) {
                        //Cualquier error al logcat
                        FacebookRequestError error = response.getError();
                        if (error != null) {
                            dismissProgressDialog();
                            Log.i(TAG, error.getErrorMessage());
                        }
                    }
                };

                // Crea el request para la creacion del objeto
                Request objectRequest = new Request(Session.getActiveSession(),
                        "me/objects/video.other", objectParams,
                        HttpMethod.POST, objectCallback);

                // Este nombre servira para despues
                objectRequest.setBatchEntryName("objectCreate");

                // Agregamos el request para que sea procesado
                requestBatch.add(objectRequest);


                Bundle actionParams = new Bundle();
                // Hace referencia al objeto anteriormente creado
                actionParams.putString("other", "{result=objectCreate:$.id}");
                actionParams.putString("fb:explicitly_shared", "true");

                Request.Callback actionCallback = new Request.Callback() {

                    @Override
                    public void onCompleted(Response response) {
                        dismissProgressDialog();
                        FacebookRequestError error = response.getError();
                        if (error != null) {
                            Toast.makeText(getActivity()
                                            .getApplicationContext(),
                                    error.getErrorMessage(),
                                    Toast.LENGTH_LONG).show();
                            error.getException().printStackTrace();
                        } else {
                            String actionId = null;
                            try {
                                JSONObject graphResponse = response
                                        .getGraphObject()
                                        .getInnerJSONObject();
                                actionId = graphResponse.getString("id");
                            } catch (JSONException e) {
                                Log.i(TAG,
                                        "JSON error "+ e.getMessage());
                            }
                            Toast.makeText(getActivity()
                                            .getApplicationContext(),
                                    actionId,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                };

                // Crea request para publicar
                //Ojo aqui esta direccion se forma del nombre de tu namespace:nombre de la accion
                //Verifica tu aplicacion facebook para recordar esos parametros
                Request actionRequest = new Request(Session.getActiveSession(),
                        "me/hksocialsensei:ver", actionParams, HttpMethod.POST,
                        actionCallback);

                // Agrega el request
                requestBatch.add(actionRequest);

                // Ejecuta el request
                requestBatch.executeAsync();
            } catch (JSONException e) {
                Log.i(TAG,
                        "JSON error "+ e.getMessage());
                dismissProgressDialog();
            }
        }
    }

    /*
     * Helper method to dismiss the progress dialog.
     */
    private void dismissProgressDialog() {
        // Dismiss the progress dialog
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    /*
     * Helper method to check a collection for a string.
     */
    private boolean isSubsetOf(Collection<String> subset, Collection<String> superset) {
        for (String string : subset) {
            if (!superset.contains(string)) {
                return false;
            }
        }
        return true;
    }
}
