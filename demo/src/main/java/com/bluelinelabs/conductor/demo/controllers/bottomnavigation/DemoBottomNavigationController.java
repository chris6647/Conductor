package com.bluelinelabs.conductor.demo.controllers.bottomnavigation;

import android.os.Bundle;
import android.support.annotation.IdRes;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.demo.R;

import java.lang.reflect.Constructor;

/**
 * An implementation of the bottom navigation controller
 * @author chris6647@gmail.com
 */
public class DemoBottomNavigationController extends BottomNavigationController {

  public static final String TAG = "DemoBottomNavi";

  public DemoBottomNavigationController() {
    super(R.menu.navigation);
  }

  /**
   * Supplied MenuItemId must match a {@link Controller} as defined in {@link
   * BottomNavigationMenuItem} or an {@link IllegalArgumentException} will be thrown.
   *
   * @param itemId
   */
  @Override
  protected Controller getControllerFor(@IdRes int itemId) {
    Constructor[] constructors =
        BottomNavigationMenuItem.getEnum(itemId).getControllerClass().getConstructors();
    Controller controller = null;
    try {
      /* Determine default or Bundle constructor */
      for (Constructor constructor : constructors) {
         if (constructor.getParameterTypes().length == 0) {
          controller = (Controller) constructor.newInstance();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "An exception occurred while creating a new instance for mapping of "
              + itemId
              + ". "
              + e.getMessage(),
          e);
    }

    if(controller == null){
      throw new RuntimeException(
          "Controller must have a public empty constructor. "
              + itemId);
    }
    return controller;
  }

  /**
   * Supplied Controller must match a MenuItemId as defined in {@link BottomNavigationMenuItem} or
   * an {@link IllegalArgumentException} will be thrown.
   *
   * @param controller
   */
  public void navigateTo(Controller controller) {
    BottomNavigationMenuItem item = BottomNavigationMenuItem.getEnum(controller.getClass());
    navigateTo(item.getMenuResId(), controller);
  }

}
