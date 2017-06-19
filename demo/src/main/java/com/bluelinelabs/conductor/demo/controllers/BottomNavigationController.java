package com.bluelinelabs.conductor.demo.controllers;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.MenuRes;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.bluelinelabs.conductor.ChangeHandlerFrameLayout;
import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.Router;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.demo.R;
import com.bluelinelabs.conductor.demo.controllers.base.BaseController;
import com.bluelinelabs.conductor.demo.util.BundleBuilder;

import butterknife.BindView;

/**
 * The {@link Controller} for the Bottom Navigation View.
 * Populates a {@link BottomNavigationView} with the supplied {@link Menu} resource.
 * The first item set as checked will be shown by default.
 * The backstack of each {@link MenuItem} is switched out, in order to maintain a separate
 * backstack for each {@link MenuItem} - even though that is against the Google Design Guidelines:
 *
 * @see <a href="https://material.io/guidelines/components/bottom-navigation.html#bottom-navigation-behavior">Material
 * Design Guidelines</a>
 *
 * @author chris6647@gmail.com
 */
public class BottomNavigationController extends BaseController {

    public static final String TAG = "BottomNavigationController";

    private static final String KEY_MENU_RESOURCE = "key_menu_resource";
    private static final String KEY_STATE_ROUTER_BUNDLES = "key_state_router_bundles";
    private static final String KEY_STATE_CURRENTLY_SELECTED_ID = "key_state_currently_selected_id";

    @BindView(R.id.bottom_navigation_root)
    LinearLayout bottomNavigationRoot;

    @BindView(R.id.navigation)
    BottomNavigationView bottomNavigationView;

    @BindView(R.id.bottom_navigation_controller_container)
    ChangeHandlerFrameLayout controllerContainer;

    private int currentlySelectedItemId;

    private SparseArray<Bundle> routerBundles;

    private Router childRouter;

    public BottomNavigationController(@MenuRes int menu) {
        this(new BundleBuilder(new Bundle()).putInt(KEY_MENU_RESOURCE, menu).build());
    }

    public BottomNavigationController(Bundle args) {
        super(args);
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_bottom_navigation, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        /* Setup the BottomNavigationView with the constructor supplied Menu resource */
        bottomNavigationView.inflateMenu(getMenuResource());

        Menu menu = bottomNavigationView.getMenu();
        int menuSize = menu.size();

        childRouter = getChildRouter(controllerContainer);

        /*
         * Not having access to Backstack or RouterTransaction constructors,
         * we have to save/restore the entire routers for each backstack.
         */
        if (routerBundles == null) {
            routerBundles = new SparseArray<>(menuSize);
            for (int i = 0; i < menuSize; i++) {
                MenuItem menuItem = menu.getItem(i);
                int itemId = menuItem.getItemId();
                /* Ensure the first checked item is shown */
                if (menuItem.isChecked()) {
                    childRouter.setRoot(RouterTransaction.with(BottomNavigationController.getControllerFor(
                            itemId)));
                    bottomNavigationView.setSelectedItemId(itemId);
                    currentlySelectedItemId = bottomNavigationView.getSelectedItemId();
                    break;
                }
            }
        } else {
            /*
             * Since we are restoring our state,
             * and onRestoreInstanceState is called before onViewBound,
             * all we need to do is rebind.
             */
            childRouter.rebindIfNeeded();
        }

        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (currentlySelectedItemId != item.getItemId()) {
                    saveChildRouter(currentlySelectedItemId);
                    clearChildRouter();

                    currentlySelectedItemId = item.getItemId();
                    Bundle routerBundle = routerBundles.get(currentlySelectedItemId);
                    if (routerBundle != null && !routerBundle.isEmpty()) {
                        childRouter.restoreInstanceState(routerBundle);
                        childRouter.rebindIfNeeded();
                    } else {
                        childRouter.setRoot(RouterTransaction.with(BottomNavigationController.getControllerFor(
                                currentlySelectedItemId)));
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    private void saveChildRouter(int itemId) {
        Bundle routerBundle = new Bundle();
        childRouter.saveInstanceState(routerBundle);
        routerBundles.put(itemId, routerBundle);
    }

    /**
     * Removes ALL {@link Controller}'s in the child{@link Router}'s backstack
     */
    private void clearChildRouter() {
        childRouter.setPopsLastView(true); /* Ensure the last view can be removed while we do this */
        childRouter.popToRoot();
        childRouter.popCurrentController();
        childRouter.setPopsLastView(false);
    }

    private int getMenuResource() {
        return getArgs().getInt(KEY_MENU_RESOURCE);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        routerBundles = savedInstanceState.getSparseParcelableArray(KEY_STATE_ROUTER_BUNDLES);
        currentlySelectedItemId = savedInstanceState.getInt(KEY_STATE_CURRENTLY_SELECTED_ID);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        saveChildRouter(currentlySelectedItemId);
        outState.putSparseParcelableArray(KEY_STATE_ROUTER_BUNDLES, routerBundles);
        /*
         * For some reason the BottomNavigationView does not seem to correctly restore its
         * selectedId, even though the view appears with the correct state.
         * So we keep track of it manually
         */
        outState.putInt(KEY_STATE_CURRENTLY_SELECTED_ID, currentlySelectedItemId);
    }

    @Override
    public boolean handleBack() {
        /*
         * The childRouter should handleBack,
         * as this BottomNavigationController doesn't have a back step sensible to the user.
         */
        return childRouter.handleBack();
    }

    private static Controller getControllerFor(int menuItemId) {
        Controller controller;
        switch (menuItemId) {
            case R.id.navigation_home:
                controller = new DepthChildController(Color.BLUE, 0);
                break;
            case R.id.navigation_friends:
                controller = new DepthChildController(Color.RED, 0);
                break;
            case R.id.navigation_intro:
                controller = new DepthChildController(Color.YELLOW, 0);
                break;
            case R.id.navigation_planner:
                controller = new DepthChildController(Color.GRAY, 0);
                break;
            case R.id.navigation_settings:
                controller = new DepthChildController(Color.GREEN, 0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown bottomNavigationView item selected.");
        }
        return controller;
    }
}