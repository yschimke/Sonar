// Copyright 2004-present Facebook. All Rights Reserved.

package com.facebook.litho.sonar;

import static com.facebook.litho.annotations.ImportantForAccessibility.IMPORTANT_FOR_ACCESSIBILITY_NO;
import static com.facebook.litho.annotations.ImportantForAccessibility.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
import static com.facebook.sonar.plugins.inspector.InspectorValue.Type.Color;
import static com.facebook.sonar.plugins.inspector.InspectorValue.Type.Enum;
import static com.facebook.sonar.plugins.inspector.InspectorValue.Type.Number;

import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.util.Pair;
import android.view.View;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentLifecycle;
import com.facebook.litho.DebugComponent;
import com.facebook.litho.DebugLayoutNode;
import com.facebook.litho.LithoView;
import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.State;
import com.facebook.litho.reference.Reference;
import com.facebook.sonar.core.SonarDynamic;
import com.facebook.sonar.core.SonarObject;
import com.facebook.sonar.plugins.inspector.HighlightedOverlay;
import com.facebook.sonar.plugins.inspector.InspectorValue;
import com.facebook.sonar.plugins.inspector.Named;
import com.facebook.sonar.plugins.inspector.NodeDescriptor;
import com.facebook.sonar.plugins.inspector.Touch;
import com.facebook.sonar.plugins.inspector.descriptors.ObjectDescriptor;
import com.facebook.sonar.plugins.inspector.descriptors.utils.AccessibilityUtil;
import com.facebook.yoga.YogaAlign;
import com.facebook.yoga.YogaDirection;
import com.facebook.yoga.YogaEdge;
import com.facebook.yoga.YogaFlexDirection;
import com.facebook.yoga.YogaJustify;
import com.facebook.yoga.YogaPositionType;
import com.facebook.yoga.YogaValue;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class DebugComponentDescriptor extends NodeDescriptor<DebugComponent> {

  private Map<String, List<Pair<String[], SonarDynamic>>> mOverrides = new HashMap<>();
  private DebugComponent.Overrider mOverrider =
      new DebugComponent.Overrider() {
        @Override
        public void applyComponentOverrides(String key, Component component) {
          final List<Pair<String[], SonarDynamic>> overrides = mOverrides.get(key);
          if (overrides == null) {
            return;
          }

          for (Pair<String[], SonarDynamic> override : overrides) {
            if (override.first[0].equals("Props")) {
              applyReflectiveOverride(component, override.first[1], override.second);
            }
          }
        }

        @Override
        public void applyStateOverrides(
            String key, ComponentLifecycle.StateContainer stateContainer) {
          final List<Pair<String[], SonarDynamic>> overrides = mOverrides.get(key);
          if (overrides == null) {
            return;
          }

          for (Pair<String[], SonarDynamic> override : overrides) {
            if (override.first[0].equals("State")) {
              applyReflectiveOverride(stateContainer, override.first[1], override.second);
            }
          }
        }

        @Override
        public void applyLayoutOverrides(String key, DebugLayoutNode node) {
          final List<Pair<String[], SonarDynamic>> overrides = mOverrides.get(key);
          if (overrides == null) {
            return;
          }

          for (Pair<String[], SonarDynamic> override : overrides) {
            if (override.first[0].equals("Layout")) {
              try {
                applyLayoutOverride(
                    node,
                    Arrays.copyOfRange(override.first, 1, override.first.length),
                    override.second);
              } catch (Exception ignored) {
              }
            } else if (override.first[0].equals("Accessibility")) {
              applyAccessibilityOverride(node, override.first[1], override.second);
            }
          }
        }
      };

  @Override
  public void init(DebugComponent node) {
    // We rely on the LithoView being invalidated when a component hierarchy changes.
  }

  @Override
  public String getId(DebugComponent node) {
    return node.getGlobalKey();
  }

  @Override
  public String getName(DebugComponent node) throws Exception {
    NodeDescriptor componentDescriptor = descriptorForClass(node.getComponent().getClass());
    if (componentDescriptor.getClass() != ObjectDescriptor.class) {
      return componentDescriptor.getName(node.getComponent());
    }
    return node.getComponent().getSimpleName();
  }

  @Override
  public String getAXName(DebugComponent node) throws Exception {
    NodeDescriptor componentDescriptor = descriptorForClass(node.getComponent().getClass());
    if (componentDescriptor.getClass() != ObjectDescriptor.class) {
      return componentDescriptor.getAXName(node.getComponent());
    }
    return node.getComponent().getSimpleName();
  }

  @Override
  public int getChildCount(DebugComponent node) {
    if (node.getMountedView() != null || node.getMountedDrawable() != null) {
      return 1;
    } else {
      return node.getChildComponents().size();
    }
  }

  @Override
  public int getAXChildCount(DebugComponent node) {
    if (node.getMountedView() != null) {
      return 1;
    } else {
      return node.getChildComponents().size();
    }
  }

  @Override
  public Object getChildAt(DebugComponent node, int index) {
    final View mountedView = node.getMountedView();
    final Drawable mountedDrawable = node.getMountedDrawable();

    if (mountedView != null) {
      return mountedView;
    } else if (mountedDrawable != null) {
      return mountedDrawable;
    } else {
      return node.getChildComponents().get(index);
    }
  }

  @Override
  public Object getAXChildAt(DebugComponent node, int index) {
    final View mountedView = node.getMountedView();

    if (mountedView != null) {
      return mountedView;
    } else {
      return node.getChildComponents().get(index);
    }
  }

  @Override
  public List<Named<SonarObject>> getData(DebugComponent node) throws Exception {
    NodeDescriptor componentDescriptor = descriptorForClass(node.getComponent().getClass());
    if (componentDescriptor.getClass() != ObjectDescriptor.class) {
      return componentDescriptor.getData(node.getComponent());
    }

    final List<Named<SonarObject>> data = new ArrayList<>();

    final SonarObject layoutData = getLayoutData(node);
    if (layoutData != null) {
      data.add(new Named<>("Layout", layoutData));
    }

    final SonarObject propData = getPropData(node);
    if (propData != null) {
      data.add(new Named<>("Props", propData));
    }

    final SonarObject stateData = getStateData(node);
    if (stateData != null) {
      data.add(new Named<>("State", stateData));
    }

    final SonarObject accessibilityData = getAccessibilityData(node);
    if (accessibilityData != null) {
      data.add(new Named<>("Accessibility", accessibilityData));
    }

    return data;
  }

  @Override
  public List<Named<SonarObject>> getAXData(DebugComponent node) throws Exception {
    NodeDescriptor componentDescriptor = descriptorForClass(node.getComponent().getClass());
    if (componentDescriptor.getClass() != ObjectDescriptor.class) {
      return componentDescriptor.getAXData(node.getComponent());
    }
    final List<Named<SonarObject>> data = new ArrayList<>();
    final SonarObject accessibilityData = getAccessibilityData(node);
    if (accessibilityData != null) {
      data.add(new Named<>("Accessibility", accessibilityData));
    }
    return data;
  }

  @Nullable
  private static SonarObject getLayoutData(DebugComponent node) {
    final DebugLayoutNode layout = node.getLayoutNode();
    if (layout == null) {
      return null;
    }

    final SonarObject.Builder data = new SonarObject.Builder();
    data.put("background", fromReference(node.getContext(), layout.getBackground()));
    data.put("foreground", fromDrawable(layout.getForeground()));

    data.put("direction", InspectorValue.mutable(Enum, layout.getLayoutDirection().toString()));
    data.put("flex-direction", InspectorValue.mutable(Enum, layout.getFlexDirection().toString()));
    data.put(
        "justify-content", InspectorValue.mutable(Enum, layout.getJustifyContent().toString()));
    data.put("align-items", InspectorValue.mutable(Enum, layout.getAlignItems().toString()));
    data.put("align-self", InspectorValue.mutable(Enum, layout.getAlignSelf().toString()));
    data.put("align-content", InspectorValue.mutable(Enum, layout.getAlignContent().toString()));
    data.put("position-type", InspectorValue.mutable(Enum, layout.getPositionType().toString()));

    data.put("flex-grow", fromFloat(layout.getFlexGrow()));
    data.put("flex-shrink", fromFloat(layout.getFlexShrink()));
    data.put("flex-basis", fromYogaValue(layout.getFlexBasis()));

    data.put("width", fromYogaValue(layout.getWidth()));
    data.put("min-width", fromYogaValue(layout.getMinWidth()));
    data.put("max-width", fromYogaValue(layout.getMaxWidth()));

    data.put("height", fromYogaValue(layout.getHeight()));
    data.put("min-height", fromYogaValue(layout.getMinHeight()));
    data.put("max-height", fromYogaValue(layout.getMaxHeight()));

    data.put("aspect-ratio", fromFloat(layout.getAspectRatio()));

    data.put(
        "margin",
        new SonarObject.Builder()
            .put("left", fromYogaValue(layout.getMargin(YogaEdge.LEFT)))
            .put("top", fromYogaValue(layout.getMargin(YogaEdge.TOP)))
            .put("right", fromYogaValue(layout.getMargin(YogaEdge.RIGHT)))
            .put("bottom", fromYogaValue(layout.getMargin(YogaEdge.BOTTOM)))
            .put("start", fromYogaValue(layout.getMargin(YogaEdge.START)))
            .put("end", fromYogaValue(layout.getMargin(YogaEdge.END)))
            .put("horizontal", fromYogaValue(layout.getMargin(YogaEdge.HORIZONTAL)))
            .put("vertical", fromYogaValue(layout.getMargin(YogaEdge.VERTICAL)))
            .put("all", fromYogaValue(layout.getMargin(YogaEdge.ALL))));

    data.put(
        "padding",
        new SonarObject.Builder()
            .put("left", fromYogaValue(layout.getPadding(YogaEdge.LEFT)))
            .put("top", fromYogaValue(layout.getPadding(YogaEdge.TOP)))
            .put("right", fromYogaValue(layout.getPadding(YogaEdge.RIGHT)))
            .put("bottom", fromYogaValue(layout.getPadding(YogaEdge.BOTTOM)))
            .put("start", fromYogaValue(layout.getPadding(YogaEdge.START)))
            .put("end", fromYogaValue(layout.getPadding(YogaEdge.END)))
            .put("horizontal", fromYogaValue(layout.getPadding(YogaEdge.HORIZONTAL)))
            .put("vertical", fromYogaValue(layout.getPadding(YogaEdge.VERTICAL)))
            .put("all", fromYogaValue(layout.getPadding(YogaEdge.ALL))));

    data.put(
        "border",
        new SonarObject.Builder()
            .put("left", fromFloat(layout.getBorderWidth(YogaEdge.LEFT)))
            .put("top", fromFloat(layout.getBorderWidth(YogaEdge.TOP)))
            .put("right", fromFloat(layout.getBorderWidth(YogaEdge.RIGHT)))
            .put("bottom", fromFloat(layout.getBorderWidth(YogaEdge.BOTTOM)))
            .put("start", fromFloat(layout.getBorderWidth(YogaEdge.START)))
            .put("end", fromFloat(layout.getBorderWidth(YogaEdge.END)))
            .put("horizontal", fromFloat(layout.getBorderWidth(YogaEdge.HORIZONTAL)))
            .put("vertical", fromFloat(layout.getBorderWidth(YogaEdge.VERTICAL)))
            .put("all", fromFloat(layout.getBorderWidth(YogaEdge.ALL))));

    data.put(
        "position",
        new SonarObject.Builder()
            .put("left", fromYogaValue(layout.getPosition(YogaEdge.LEFT)))
            .put("top", fromYogaValue(layout.getPosition(YogaEdge.TOP)))
            .put("right", fromYogaValue(layout.getPosition(YogaEdge.RIGHT)))
            .put("bottom", fromYogaValue(layout.getPosition(YogaEdge.BOTTOM)))
            .put("start", fromYogaValue(layout.getPosition(YogaEdge.START)))
            .put("end", fromYogaValue(layout.getPosition(YogaEdge.END)))
            .put("horizontal", fromYogaValue(layout.getPosition(YogaEdge.HORIZONTAL)))
            .put("vertical", fromYogaValue(layout.getPosition(YogaEdge.VERTICAL)))
            .put("all", fromYogaValue(layout.getPosition(YogaEdge.ALL))));

    return data.build();
  }

  @Nullable
  private static SonarObject getPropData(DebugComponent node) {
    if (node.canResolve()) {
      return null;
    }

    final Component component = node.getComponent();
    final SonarObject.Builder props = new SonarObject.Builder();

    boolean hasProps = false;
    for (Field f : component.getClass().getDeclaredFields()) {
      try {
        f.setAccessible(true);

        final Prop annotation = f.getAnnotation(Prop.class);
        if (annotation != null) {
          switch (annotation.resType()) {
            case COLOR:
              props.put(f.getName(), fromColor((Integer) f.get(component)));
              break;
            case DRAWABLE:
              props.put(f.getName(), fromDrawable((Drawable) f.get(component)));
              break;
            default:
              if (f.get(component) != null
                  && PropWithDescription.class.isAssignableFrom(f.get(component).getClass())) {
                final Object description =
                    ((PropWithDescription) f.get(component))
                        .getSonarLayoutInspectorPropDescription();
                // Treat the description as immutable for now, because it's a "translation" of the
                // actual prop,
                // mutating them is not going to change the original prop.
                if (description instanceof Map<?, ?>) {
                  final Map<?, ?> descriptionMap = (Map<?, ?>) description;
                  for (Map.Entry<?, ?> entry : descriptionMap.entrySet()) {
                    props.put(
                        entry.getKey().toString(), InspectorValue.immutable(entry.getValue()));
                  }
                } else {
                  props.put(f.getName(), InspectorValue.immutable(description));
                }
              } else {
                if (isTypeMutable(f.getType())) {
                  props.put(f.getName(), InspectorValue.mutable(f.get(component)));
                } else {
                  props.put(f.getName(), InspectorValue.immutable(f.get(component)));
                }
              }
              break;
          }
          hasProps = true;
        }
      } catch (Exception ignored) {
      }
    }

    return hasProps ? props.build() : null;
  }

  @Nullable
  private static SonarObject getStateData(DebugComponent node) {
    if (node.canResolve()) {
      return null;
    }

    final ComponentLifecycle.StateContainer stateContainer = node.getStateContainer();
    if (stateContainer == null) {
      return null;
    }

    final SonarObject.Builder state = new SonarObject.Builder();

    boolean hasState = false;
    for (Field f : stateContainer.getClass().getDeclaredFields()) {
      try {
        f.setAccessible(true);

        final State annotation = f.getAnnotation(State.class);
        if (annotation != null) {
          if (isTypeMutable(f.getType())) {
            state.put(f.getName(), InspectorValue.mutable(f.get(stateContainer)));
          } else {
            state.put(f.getName(), InspectorValue.immutable(f.get(stateContainer)));
          }
          hasState = true;
        }
      } catch (Exception ignored) {
      }
    }

    return hasState ? state.build() : null;
  }

  private static boolean isTypeMutable(Class<?> type) {
    if (type == int.class || type == Integer.class) {
      return true;
    } else if (type == long.class || type == Long.class) {
      return true;
    } else if (type == float.class || type == Float.class) {
      return true;
    } else if (type == double.class || type == Double.class) {
      return true;
    } else if (type == boolean.class || type == Boolean.class) {
      return true;
    } else if (type.isAssignableFrom(String.class)) {
      return true;
    }
    return false;
  }

  @Nullable
  private static SonarObject getAccessibilityData(DebugComponent node) {
    final DebugLayoutNode layout = node.getLayoutNode();
    if (layout == null) {
      return null;
    }

    final View hostView = node.getComponentHost();
    final SonarObject.Builder accessibilityProps = new SonarObject.Builder();

    // This needs to be an empty string to be mutable. See t20470623.
    final CharSequence contentDescription =
        layout.getContentDescription() != null ? layout.getContentDescription() : "";
    accessibilityProps.put("content-description", InspectorValue.mutable(contentDescription));
    accessibilityProps.put("focusable", InspectorValue.mutable(layout.getFocusable()));
    accessibilityProps.put(
        "important-for-accessibility",
        AccessibilityUtil.sImportantForAccessibilityMapping.get(
            layout.getImportantForAccessibility()));

    // No host view exists, so this component is inherently not accessible.  Add the reason why this
    // is the case and then return.
    if (hostView == node.getLithoView() || hostView == null) {
      final int importantForAccessibility = layout.getImportantForAccessibility();
      final boolean isAccessibilityEnabled =
          AccessibilityUtil.isAccessibilityEnabled(node.getContext());
      String ignoredReason;

      if (!isAccessibilityEnabled) {
        ignoredReason = "No accessibility service is running.";
      } else if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_NO) {
        ignoredReason = "Component has importantForAccessibility set to NO.";
      } else if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
        ignoredReason = "Component has importantForAccessibility set to NO_HIDE_DESCENDANTS.";
      } else {
        ignoredReason = "Component does not have content, or accessibility handlers.";
      }

      accessibilityProps.put("talkback-ignored", true);
      accessibilityProps.put("talkback-ignored-reasons", ignoredReason);

      return accessibilityProps.build();
    }

    accessibilityProps.put(
        "node-info", AccessibilityUtil.getAccessibilityNodeInfoProperties(hostView));
    AccessibilityUtil.addTalkbackProperties(accessibilityProps, hostView);

    return accessibilityProps.build();
  }

  @Override
  public void setValue(DebugComponent node, String[] path, SonarDynamic value) {
    List<Pair<String[], SonarDynamic>> overrides = mOverrides.get(node.getGlobalKey());
    if (overrides == null) {
      overrides = new ArrayList<>();
      mOverrides.put(node.getGlobalKey(), overrides);
    }
    overrides.add(new Pair<>(path, value));

    node.setOverrider(mOverrider);
    node.rerender();
  }

  @Override
  public List<Named<String>> getAttributes(DebugComponent node) {
    final List<Named<String>> attributes = new ArrayList<>();
    final String key = node.getKey();
    final String testKey = node.getTestKey();

    if (key != null && key.trim().length() > 0) {
      attributes.add(new Named<>("key", key));
    }

    if (testKey != null && testKey.trim().length() > 0) {
      attributes.add(new Named<>("testKey", testKey));
    }

    return attributes;
  }

  @Override
  public List<Named<String>> getAXAttributes(DebugComponent node) {
    return Collections.EMPTY_LIST;
  }

  @Override
  public void setHighlighted(DebugComponent node, boolean selected) {
    final LithoView lithoView = node.getLithoView();
    if (lithoView == null) {
      return;
    }

    if (!selected) {
      HighlightedOverlay.removeHighlight(lithoView);
      return;
    }

    final DebugLayoutNode layout = node.getLayoutNode();
    final boolean hasNode = layout != null;
    final Rect margin;
    if (!node.isRoot()) {
      margin =
          new Rect(
              hasNode ? (int) layout.getResultMargin(YogaEdge.START) : 0,
              hasNode ? (int) layout.getResultMargin(YogaEdge.TOP) : 0,
              hasNode ? (int) layout.getResultMargin(YogaEdge.END) : 0,
              hasNode ? (int) layout.getResultMargin(YogaEdge.BOTTOM) : 0);
    } else {
      // Margin not applied if you're at the root
      margin = new Rect();
    }

    final Rect padding =
        new Rect(
            hasNode ? (int) layout.getResultPadding(YogaEdge.START) : 0,
            hasNode ? (int) layout.getResultPadding(YogaEdge.TOP) : 0,
            hasNode ? (int) layout.getResultPadding(YogaEdge.END) : 0,
            hasNode ? (int) layout.getResultPadding(YogaEdge.BOTTOM) : 0);

    final Rect contentBounds = node.getBoundsInLithoView();
    HighlightedOverlay.setHighlighted(lithoView, margin, padding, contentBounds);
  }

  @Override
  public void hitTest(DebugComponent node, Touch touch) {
    for (int i = getChildCount(node) - 1; i >= 0; i--) {
      final Object child = getChildAt(node, i);
      if (child instanceof DebugComponent) {
        final DebugComponent componentChild = (DebugComponent) child;
        final Rect bounds = componentChild.getBounds();

        if (touch.containedIn(bounds.left, bounds.top, bounds.right, bounds.bottom)) {
          touch.continueWithOffset(i, bounds.left, bounds.top);
          return;
        }
      } else if (child instanceof View || child instanceof Drawable) {
        // Components can only mount one view or drawable and its bounds are the same as the
        // hosting component.
        touch.continueWithOffset(i, 0, 0);
        return;
      }
    }

    touch.finish();
  }

  @Override
  public String getDecoration(DebugComponent node) throws Exception {
    if (node.getComponent() != null) {
      NodeDescriptor componentDescriptor = descriptorForClass(node.getComponent().getClass());
      if (componentDescriptor.getClass() != ObjectDescriptor.class) {
        return componentDescriptor.getDecoration(node.getComponent());
      }
    }
    return "litho";
  }

  @Override
  public boolean matches(String query, DebugComponent node) throws Exception {
    NodeDescriptor descriptor = descriptorForClass(Object.class);
    return descriptor.matches(query, node);
  }

  private static void applyAccessibilityOverride(
      DebugLayoutNode node, String key, SonarDynamic value) {
    switch (key) {
      case "focusable":
        node.setFocusable(value.asBoolean());
        break;
      case "important-for-accessibility":
        node.setImportantForAccessibility(
            AccessibilityUtil.sImportantForAccessibilityMapping.get(value.asString()));
        break;
      case "content-description":
        node.setContentDescription(value.asString());
        break;
    }
  }

  private static void applyLayoutOverride(DebugLayoutNode node, String[] path, SonarDynamic value) {
    switch (path[0]) {
      case "background":
        node.setBackgroundColor(value.asInt());
        break;
      case "foreground":
        node.setForegroundColor(value.asInt());
        break;
      case "direction":
        node.setLayoutDirection(YogaDirection.valueOf(value.asString().toUpperCase()));
        break;
      case "flex-direction":
        node.setFlexDirection(YogaFlexDirection.valueOf(value.asString().toUpperCase()));
        break;
      case "justify-content":
        node.setJustifyContent(YogaJustify.valueOf(value.asString().toUpperCase()));
        break;
      case "align-items":
        node.setAlignItems(YogaAlign.valueOf(value.asString().toUpperCase()));
        break;
      case "align-self":
        node.setAlignSelf(YogaAlign.valueOf(value.asString().toUpperCase()));
        break;
      case "align-content":
        node.setAlignContent(YogaAlign.valueOf(value.asString().toUpperCase()));
        break;
      case "position-type":
        node.setPositionType(YogaPositionType.valueOf(value.asString().toUpperCase()));
        break;
      case "flex-grow":
        node.setFlexGrow(value.asFloat());
        break;
      case "flex-shrink":
        node.setFlexShrink(value.asFloat());
        break;
      case "flex-basis":
        node.setFlexBasis(YogaValue.parse(value.asString()));
        break;
      case "width":
        node.setWidth(YogaValue.parse(value.asString()));
        break;
      case "min-width":
        node.setMinWidth(YogaValue.parse(value.asString()));
        break;
      case "max-width":
        node.setMaxWidth(YogaValue.parse(value.asString()));
        break;
      case "height":
        node.setHeight(YogaValue.parse(value.asString()));
        break;
      case "min-height":
        node.setMinHeight(YogaValue.parse(value.asString()));
        break;
      case "max-height":
        node.setMaxHeight(YogaValue.parse(value.asString()));
        break;
      case "aspect-ratio":
        node.setAspectRatio(value.asFloat());
        break;
      case "margin":
        node.setMargin(edgeFromString(path[1]), YogaValue.parse(value.asString()));
        break;
      case "padding":
        node.setPadding(edgeFromString(path[1]), YogaValue.parse(value.asString()));
        break;
      case "border":
        node.setBorderWidth(edgeFromString(path[1]), value.asFloat());
        break;
      case "position":
        node.setPosition(edgeFromString(path[1]), YogaValue.parse(value.asString()));
        break;
    }
  }

  private static YogaEdge edgeFromString(String s) {
    return YogaEdge.valueOf(s.toUpperCase());
  }

  private static void applyReflectiveOverride(Object o, String key, SonarDynamic dynamic) {
    try {
      final Field field = o.getClass().getDeclaredField(key);
      field.setAccessible(true);

      final Class type = field.getType();

      Object value = null;
      if (type == int.class || type == Integer.class) {
        value = dynamic.asInt();
      } else if (type == long.class || type == Long.class) {
        value = dynamic.asLong();
      } else if (type == float.class || type == Float.class) {
        value = dynamic.asFloat();
      } else if (type == double.class || type == Double.class) {
        value = dynamic.asDouble();
      } else if (type == boolean.class || type == Boolean.class) {
        value = dynamic.asBoolean();
      } else if (type.isAssignableFrom(String.class)) {
        value = dynamic.asString();
      }

      if (value != null) {
        field.set(o, value);
      }
    } catch (Exception ignored) {
    }
  }

  private static InspectorValue fromDrawable(Drawable d) {
    if (d instanceof ColorDrawable) {
      return InspectorValue.mutable(Color, ((ColorDrawable) d).getColor());
    }
    return InspectorValue.mutable(Color, 0);
  }

  private static <T extends Drawable> InspectorValue fromReference(
      ComponentContext c, Reference<T> r) {
    if (r == null) {
      return fromDrawable(null);
    }

    final T d = Reference.acquire(c, r);
    final InspectorValue v = fromDrawable(d);
    Reference.release(c, d, r);
    return v;
  }

  private static InspectorValue fromFloat(float f) {
    if (Float.isNaN(f)) {
      return InspectorValue.mutable(Enum, "undefined");
    }
    return InspectorValue.mutable(Number, f);
  }

  private static InspectorValue fromYogaValue(YogaValue v) {
    // TODO add support for Type.Dimension or similar
    return InspectorValue.mutable(Enum, v.toString());
  }

  private static InspectorValue fromColor(int color) {
    return InspectorValue.mutable(Color, color);
  }
}
