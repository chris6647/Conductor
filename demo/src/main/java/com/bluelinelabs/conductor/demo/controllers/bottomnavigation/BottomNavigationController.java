package com.bluelinelabs.conductor.demo.controllers.bottomnavigation;

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
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler;
import com.bluelinelabs.conductor.demo.R;
import com.bluelinelabs.conductor.demo.controllers.base.BaseController;
import com.bluelinelabs.conductor.demo.util.BundleBuilder;
import com.bluelinelabs.conductor.demo.util.Utils;

import butterknife.BindView;

/**
 * The {@link Controller} for the Bottom Navigation View. Populates a {@link BottomNavigationView}
 * with the supplied {@link Menu} resource. The first item set as checked will be shown by default.
 * The backstack of each {@link MenuItem} is switched out, in order to maintain a separate backstack
 * for each {@link MenuItem} - even though that is against the Google Design Guidelines:
 *
 * @see <a
 *     href="https://material.io/guidelines/components/bottom-navigation.html#bottom-navigation-behavior">Material
 *     Design Guidelines</a>
 *
 * Internally works similarly to {@link com.bluelinelabs.conductor.support.RouterPagerAdapter},
 * in the sense that it keeps track of the currently active {@link MenuItem} and the paired
 * Child {@link Router}. Everytime we navigate from one to another,
 * or {@link Controller#onSaveInstanceState(Bundle)} is called, we save the entire instance state
 * of the Child {@link Router}, and cache it, so we have it available when we navigate to
 * another {@link MenuItem} and can then restore the correct Child {@link Router}
 * (and thus the entire backstack)
 *
 * @author chris6647@gmail.com
 */
public abstract class BottomNavigationController extends BaseController {

  @SuppressWarnings("unused")
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

  private SparseArray<Bundle> routerSavedStateBundles;
  private Bundle cachedSavedInstanceState;
  private Router lastActiveChildRouter;

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

    bottomNavigationView.setOnNavigationItemSelectedListener(
        new BottomNavigationView.OnNavigationItemSelectedListener() {
          @Override
          public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            if (currentlySelectedItemId != item.getItemId()) {
              BottomNavigationController.this.destroyChildRouter(BottomNavigationController.this.getChildRouter(currentlySelectedItemId), currentlySelectedItemId);
              currentlySelectedItemId = item.getItemId();
              BottomNavigationController.this.configureRouter(BottomNavigationController.this.getChildRouter(currentlySelectedItemId), currentlySelectedItemId);
            } else {
              BottomNavigationController.this.resetCurrentBackstack();
            }
            return true;
          }
        });
  }

  @Override
  protected void onAttach(@NonNull View view) {
    super.onAttach(view);

    /* Fresh start, setup everything */
    if (routerSavedStateBundles == null) {
      Menu menu = bottomNavigationView.getMenu();
      int menuSize = menu.size();
      routerSavedStateBundles = new SparseArray<>(menuSize);
      for (int i = 0; i < menuSize; i++) {
        MenuItem menuItem = menu.getItem(i);
        /* Ensure the first checked item is shown */
        if (menuItem.isChecked()) {
          /*
           * Seems like the BottomNavigationView always initializes index 0 as isChecked / Selected,
           * regardless of what was set in the menu xml originally.
           * So basically all we're doing here is always setting up menuItem index 0.
           */
          int itemId = menuItem.getItemId();
          configureRouter(getChildRouter(itemId), itemId);
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
      Router childRouter = getChildRouter(currentlySelectedItemId);
      childRouter.rebindIfNeeded();
      lastActiveChildRouter = childRouter;
    }
  }

  /**
   * Get the Child {@link Router} matching the supplied ItemId.
   *
   * @param itemId MenuItem ID
   * @return
   */
  protected Router getChildRouter(int itemId) {
    return getChildRouter(controllerContainer, makeRouterName(controllerContainer.getId(), itemId));
  }

  /**
   * Correctly configure the {@link Router} given the cached routerSavedState.
   *
   * @param childRouter {@link Router} to configure
   * @param itemId {@link MenuItem} ID
   * @return true if {@link Router} was restored
   */
  private boolean configureRouter(@NonNull Router childRouter, int itemId) {
    lastActiveChildRouter = childRouter;
    Bundle routerSavedState = routerSavedStateBundles.get(itemId);
    if (routerSavedState != null && !routerSavedState.isEmpty()) {
      childRouter.restoreInstanceState(routerSavedState);
      childRouter.rebindIfNeeded();
      return true;
    }

    if (!childRouter.hasRootController()) {
      childRouter.setRoot(RouterTransaction.with(getControllerFor(itemId)));
    }
    return false;
  }

  /**
   * Save the {@link Router}, and remove(/destroy) it.
   *
   * @param childRouter {@link Router} to destroy
   * @param itemId {@link MenuItem} ID
   */
  protected void destroyChildRouter(@NonNull Router childRouter, int itemId) {
    save(childRouter, itemId);
    removeChildRouter(childRouter);
  }

  /**
   * Resets the current backstack to the {@link Controller}, supplied by {@link
   * BottomNavigationController#getControllerFor(int)}, using a {@link FadeChangeHandler}.
   */
  protected void resetCurrentBackstack() {
    lastActiveChildRouter
        .setRoot(
            RouterTransaction.with(this.getControllerFor(currentlySelectedItemId))
                .pushChangeHandler(new FadeChangeHandler())
                .popChangeHandler(new FadeChangeHandler()));
  }

  /**
   * Navigate to the supplied {@link Controller}, while setting the menuItemId as selected on the
   * {@link BottomNavigationView}.
   *
   * @param itemId {@link MenuItem} ID
   * @param controller {@link Controller} matching the itemId
   */
  protected void navigateTo(int itemId, @NonNull Controller controller) {
    if (currentlySelectedItemId != itemId) {
      destroyChildRouter(lastActiveChildRouter, currentlySelectedItemId);

      /* Ensure correct Checked state based on new selection */
      Menu menu = bottomNavigationView.getMenu();
      for (int i = 0; i < menu.size(); i++) {
        MenuItem menuItem = menu.getItem(i);
        if (menuItem.isChecked() && menuItem.getItemId() != itemId) {
          menuItem.setChecked(false);
        } else if (menuItem.getItemId() == itemId) {
          menuItem.setChecked(true);
        }
      }

      currentlySelectedItemId = itemId;
      Router childRouter = getChildRouter(currentlySelectedItemId);
      if (configureRouter(childRouter, currentlySelectedItemId)) {
        /* Determine if a Controller of same class already exists in the backstack */
        Controller backstackController;
        int size = childRouter.getBackstackSize();
        for (int i = 0; i < size; i++) {
          backstackController = childRouter.getBackstack().get(i).controller();
          if (Utils.equals(backstackController.getClass(), controller.getClass())) {
            /* Match found at root - so just set new root */
            if (i == size - 1) {
              childRouter.setRoot(RouterTransaction.with(controller));
            } else {
              /* Match found at i - pop until we're at the matching Controller */
              for (int j = size; j < i; j--) {
                childRouter.popCurrentController();
              }
              /* Replace the existing matching Controller with the new */
              childRouter.replaceTopController(RouterTransaction.with(controller));
            }
          }
        }
      }
    } else {
      resetCurrentBackstack();
    }
  }

  /**
   * Saves the Child {@link Router} into a {@link Bundle} and caches that {@link Bundle}.
   *
   * @param childRouter to call {@link Router#saveInstanceState(Bundle)} on
   * @param itemId {@link MenuItem} ID
   */
  private void save(Router childRouter, int itemId){
    Bundle routerBundle = new Bundle();
    childRouter.saveInstanceState(routerBundle);
    routerSavedStateBundles.put(itemId, routerBundle);
  }

  @Override
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    routerSavedStateBundles = savedInstanceState.getSparseParcelableArray(KEY_STATE_ROUTER_BUNDLES);
    currentlySelectedItemId = savedInstanceState.getInt(KEY_STATE_CURRENTLY_SELECTED_ID);
    if (savedInstanceState.containsKey(KEY_STATE_ROUTER_BUNDLES)
        || savedInstanceState.containsKey(KEY_STATE_CURRENTLY_SELECTED_ID)) {
      cachedSavedInstanceState = savedInstanceState;
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    if (lastActiveChildRouter == null && cachedSavedInstanceState != null) {
      /*
       * Here we assume that we're in a state
       * where the BottomNavigationController itself is in the backstack,
       * it has been restored, and is now being saved again.
       * In this case, the BottomNavigationController won't ever have had onViewBound() called,
       * and thus won't have any views to setup the Child Routers with.
       * In this case, we assume that we've previously had onSaveInstanceState() called
       * on us successfully, and thus have a cachedSavedInstanceState to use.
       *
       * To replicate issue this solves:
       * Navigate from BottomNavigationController to another controller not hosted in
       * the childRouter, background the app
       * (with developer setting "don't keep activities in memory" enabled on the device),
       * open the app again, and background it once more, and open it again to see it crash.
       */
      outState.putSparseParcelableArray(
          KEY_STATE_ROUTER_BUNDLES,
          cachedSavedInstanceState.getSparseParcelableArray(KEY_STATE_ROUTER_BUNDLES));
      outState.putInt(
          KEY_STATE_CURRENTLY_SELECTED_ID,
          cachedSavedInstanceState.getInt(KEY_STATE_CURRENTLY_SELECTED_ID));
    } else if (currentlySelectedItemId != 0) {
      /*
       * Only save state if we have a valid item selected.
       *
       * Otherwise we may be in a state where we are in a backstack, but have never been shown.
       * I.e. if we are put in a synthesized backstack, we've never been shown any UI,
       * and therefore have nothing to save.
       */
      save(lastActiveChildRouter, currentlySelectedItemId);
      outState.putSparseParcelableArray(KEY_STATE_ROUTER_BUNDLES, routerSavedStateBundles);
      /*
       * For some reason the BottomNavigationView does not seem to correctly restore its
       * selectedId, even though the view appears with the correct state.
       * So we keep track of it manually
       */
      outState.putInt(KEY_STATE_CURRENTLY_SELECTED_ID, currentlySelectedItemId);
      lastActiveChildRouter = null;
    }
  }

  @Override
  public boolean handleBack() {
    /*
     * The childRouter should handleBack,
     * as this BottomNavigationController doesn't have a back step sensible to the user.
     */
    return lastActiveChildRouter.handleBack();
  }

  /**
   * Get the {@link Menu} Resource ID from {@link Controller#getArgs()}
   * @return the {@link Menu} Resource ID
   */
  private int getMenuResource() {
    return getArgs().getInt(KEY_MENU_RESOURCE);
  }

  /**
   * Return a target instance of {@link Controller} for given menu item ID
   *
   * @param itemId the ID tapped by the user
   * @return the {@link Controller} instance to navigate to
   */
  protected abstract Controller getControllerFor(int itemId);

  /**
   * Create an internally used name to identify the Child {@link Router}s
   * @param viewId
   * @param id
   * @return
   */
  private static String makeRouterName(int viewId, long id) {
    return viewId + ":" + id;
  }
}