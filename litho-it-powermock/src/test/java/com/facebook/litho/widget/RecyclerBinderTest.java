/**
 * Copyright (c) 2017-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.litho.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;

import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentInfo;
import com.facebook.litho.ComponentTree;
import com.facebook.litho.LayoutHandler;
import com.facebook.litho.Size;
import com.facebook.litho.SizeSpec;
import com.facebook.litho.testing.testrunner.ComponentsTestRunner;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RecyclerBinder}
 */
@RunWith(ComponentsTestRunner.class)
@PrepareForTest(ComponentTreeHolder.class)
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*"})
public class RecyclerBinderTest {

  @Rule
  public PowerMockRule mPowerMockRule = new PowerMockRule();

  private static final float RANGE_RATIO = 2.0f;
  private static final int RANGE_SIZE = 3;
  private final Map<Component, TestComponentTreeHolder> mHoldersForComponents = new HashMap<>();
  private RecyclerBinder mRecyclerBinder;
  private LayoutInfo mLayoutInfo;
  private ComponentContext mComponentContext;

  private final Answer<ComponentTreeHolder> mComponentTreeHolderAnswer =
      new Answer<ComponentTreeHolder>() {
        @Override
        public ComponentTreeHolder answer(InvocationOnMock invocation) throws Throwable {
          final ComponentInfo componentInfo = (ComponentInfo) invocation.getArguments()[0];
          final TestComponentTreeHolder holder = new TestComponentTreeHolder(componentInfo);
          mHoldersForComponents.put(componentInfo.getComponent(), holder);

          return holder;
        }
      };

  @Before
  public void setup() {
    mComponentContext = new ComponentContext(RuntimeEnvironment.application);
    PowerMockito.mockStatic(ComponentTreeHolder.class);
    PowerMockito.when(ComponentTreeHolder.acquire(
        any(ComponentInfo.class),
        any(LayoutHandler.class),
        any(boolean.class)))
        .thenAnswer(mComponentTreeHolderAnswer);
    mLayoutInfo = mock(LayoutInfo.class);
    setupBaseLayoutInfoMock();

    mRecyclerBinder = new RecyclerBinder(mComponentContext, RANGE_RATIO, mLayoutInfo);
  }

  private void setupBaseLayoutInfoMock() {
    when(mLayoutInfo.getScrollDirection()).thenReturn(OrientationHelper.VERTICAL);

    when(mLayoutInfo.getLayoutManager())
        .thenReturn(new LinearLayoutManager(mComponentContext));

    when(mLayoutInfo.approximateRangeSize(anyInt(), anyInt(), anyInt(), anyInt()))
        .thenReturn(RANGE_SIZE);

    when(mLayoutInfo.getChildHeightSpec(anyInt(), any(ComponentInfo.class)))
        .thenReturn(SizeSpec.makeSizeSpec(100, SizeSpec.EXACTLY));
    when(mLayoutInfo.getChildWidthSpec(anyInt(), any(ComponentInfo.class)))
        .thenReturn(SizeSpec.makeSizeSpec(100, SizeSpec.EXACTLY));
  }

  @Test
  public void testComponentTreeHolderCreation() {
    final List<ComponentInfo> components = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      components.add(ComponentInfo.create().component(mock(Component.class)).build());
      mRecyclerBinder.insertItemAt(0, components.get(i));
    }

    for (int i = 0; i < 100; i++) {
      Assert.assertNotNull(mHoldersForComponents.get(components.get(i).getComponent()));
    }
  }

  @Test
  public void testOnMeasureAfterAddingItems() {
    final List<ComponentInfo> components = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      components.add(ComponentInfo.create().component(mock(Component.class)).build());
      mRecyclerBinder.insertItemAt(i, components.get(i));
    }

    for (int i = 0; i < 100; i++) {
      Assert.assertNotNull(mHoldersForComponents.get(components.get(i).getComponent()));
    }

    final Size size = new Size();
    final int widthSpec = SizeSpec.makeSizeSpec(200, SizeSpec.AT_MOST);
    final int heightSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);

    mRecyclerBinder.measure(size, widthSpec, heightSpec);

    TestComponentTreeHolder componentTreeHolder =
        mHoldersForComponents.get(components.get(0).getComponent());

    assertTrue(componentTreeHolder.isTreeValid());
    assertTrue(componentTreeHolder.mLayoutSyncCalled);

    int rangeTotal = RANGE_SIZE + (int) (RANGE_SIZE * RANGE_RATIO);

    for (int i = 1; i <= rangeTotal; i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      assertTrue(componentTreeHolder.isTreeValid());
      assertTrue(componentTreeHolder.mLayoutAsyncCalled);
      assertFalse(componentTreeHolder.mLayoutSyncCalled);
    }

    for (int k = rangeTotal + 1; k < components.size(); k++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(k).getComponent());

      assertFalse(componentTreeHolder.isTreeValid());
      assertFalse(componentTreeHolder.mLayoutAsyncCalled);
      assertFalse(componentTreeHolder.mLayoutSyncCalled);
    }

    Assert.assertEquals(100, size.width);
  }

  @Test
  public void onBoundsDefined() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    for (int i = 0; i < components.size(); i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      holder.mLayoutAsyncCalled = false;
      holder.mLayoutSyncCalled = false;
    }

    mRecyclerBinder.setSize(200, 200);

    for (int i = 0; i < components.size(); i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      assertFalse(holder.mLayoutAsyncCalled);
      assertFalse(holder.mLayoutSyncCalled);
    }
  }

  @Test
  public void onBoundsDefinedWithDifferentSize() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    for (int i = 0; i < components.size(); i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      holder.mLayoutAsyncCalled = false;
      holder.mLayoutSyncCalled = false;
    }

    mRecyclerBinder.setSize(300, 200);

    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    TestComponentTreeHolder holder = mHoldersForComponents.get(components.get(0).getComponent());
    assertTrue(holder.isTreeValid());
    assertTrue(holder.mLayoutSyncCalled);

    for (int i = 1; i <= rangeTotal; i++) {
      holder = mHoldersForComponents.get(components.get(i).getComponent());
      assertTrue(holder.isTreeValid());
      assertTrue(holder.mLayoutAsyncCalled);
    }

    for (int i = rangeTotal + 1; i < components.size(); i++) {
      holder = mHoldersForComponents.get(components.get(i).getComponent());
      assertFalse(holder.isTreeValid());
      assertFalse(holder.mLayoutAsyncCalled);
      assertFalse(holder.mLayoutSyncCalled);
    }
  }

  @Test
  public void testMount() {
    RecyclerView recyclerView = mock(RecyclerView.class);
    mRecyclerBinder.mount(recyclerView);

    verify(recyclerView).setLayoutManager(mLayoutInfo.getLayoutManager());
    verify(recyclerView).setAdapter(any(RecyclerView.Adapter.class));
    verify(mLayoutInfo).setComponentInfoCollection(mRecyclerBinder);
    verify(recyclerView).addOnScrollListener(any(OnScrollListener.class));
  }

  @Test
  public void testMountWithStaleView() {
    RecyclerView recyclerView = mock(RecyclerView.class);
    mRecyclerBinder.mount(recyclerView);

    verify(recyclerView).setLayoutManager(mLayoutInfo.getLayoutManager());
    verify(recyclerView).setAdapter(any(RecyclerView.Adapter.class));
    verify(recyclerView).addOnScrollListener(any(OnScrollListener.class));

    RecyclerView secondRecyclerView = mock(RecyclerView.class);
    mRecyclerBinder.mount(secondRecyclerView);

    verify(recyclerView).setLayoutManager(null);
    verify(recyclerView).setAdapter(null);
    verify(recyclerView).removeOnScrollListener(any(OnScrollListener.class));

    verify(secondRecyclerView).setLayoutManager(mLayoutInfo.getLayoutManager());
    verify(secondRecyclerView).setAdapter(any(RecyclerView.Adapter.class));
    verify(secondRecyclerView).addOnScrollListener(any(OnScrollListener.class));
  }

  @Test
  public void testUnmount() {
    RecyclerView recyclerView = mock(RecyclerView.class);
    mRecyclerBinder.mount(recyclerView);

    verify(recyclerView).setLayoutManager(mLayoutInfo.getLayoutManager());
    verify(recyclerView).setAdapter(any(RecyclerView.Adapter.class));
    verify(recyclerView).addOnScrollListener(any(OnScrollListener.class));

    mRecyclerBinder.unmount(recyclerView);

    verify(recyclerView).setLayoutManager(null);
    verify(recyclerView).setAdapter(null);
    verify(mLayoutInfo).setComponentInfoCollection(null);
    verify(recyclerView).removeOnScrollListener(any(OnScrollListener.class));
  }

  @Test
  public void testAddStickyHeaderIfRecyclerViewWrapperExists() throws Exception {
    RecyclerView recyclerView = mock(RecyclerView.class);
    RecyclerViewWrapper wrapper = mock(RecyclerViewWrapper.class);

    when(recyclerView.getParent()).thenReturn(wrapper);
    when(recyclerView.getLayoutManager()).thenReturn(mock(RecyclerView.LayoutManager.class));
    when(wrapper.getRecyclerView()).thenReturn(recyclerView);

    mRecyclerBinder.mount(recyclerView);

    verify(recyclerView).setAdapter(any(RecyclerView.Adapter.class));
    verify(recyclerView, times(2)).addOnScrollListener(any(OnScrollListener.class));
  }

  @Test
  public void onRemeasureWithDifferentSize() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    for (int i = 0; i < components.size(); i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      holder.mLayoutAsyncCalled = false;
      holder.mLayoutSyncCalled = false;
    }

    int widthSpec = SizeSpec.makeSizeSpec(100, SizeSpec.EXACTLY);
    int heightSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);

    mRecyclerBinder.measure(new Size(), widthSpec, heightSpec);
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    TestComponentTreeHolder holder = mHoldersForComponents.get(components.get(0).getComponent());
    assertTrue(holder.isTreeValid());
    assertTrue(holder.mLayoutSyncCalled);

    for (int i = 1; i <= rangeTotal; i++) {
      holder = mHoldersForComponents.get(components.get(i).getComponent());
      assertTrue(holder.isTreeValid());
      assertTrue(holder.mLayoutAsyncCalled);
    }

    for (int i = rangeTotal + 1; i < components.size(); i++) {
      holder = mHoldersForComponents.get(components.get(i).getComponent());
      assertFalse(holder.isTreeValid());
      assertFalse(holder.mLayoutAsyncCalled);
      assertFalse(holder.mLayoutSyncCalled);
    }
  }

  @Test
  public void testComponentWithDifferentSpanSize() {
    final List<ComponentInfo> components = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      components.add(ComponentInfo.create()
          .component(mock(Component.class))
          .spanSize((i == 0 || i % 3 == 0) ? 2 : 1)
          .build());

      mRecyclerBinder.insertItemAt(i, components.get(i));
    }

    when(mLayoutInfo.getChildWidthSpec(anyInt(), any(ComponentInfo.class)))
        .thenAnswer(new Answer<Integer>() {
          @Override
          public Integer answer(InvocationOnMock invocation) throws Throwable {
            final ComponentInfo componentInfo = (ComponentInfo) invocation.getArguments()[1];
            final int spanSize = componentInfo.getSpanSize();

            return SizeSpec.makeSizeSpec(100 * spanSize, SizeSpec.EXACTLY);
          }
        });

    for (int i = 0; i < 100; i++) {
      Assert.assertNotNull(mHoldersForComponents.get(components.get(i).getComponent()));
    }

    Size size = new Size();
    int widthSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);
    int heightSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);

    mRecyclerBinder.measure(size, widthSpec, heightSpec);

    TestComponentTreeHolder componentTreeHolder =
        mHoldersForComponents.get(components.get(0).getComponent());

    assertTrue(componentTreeHolder.isTreeValid());
    assertTrue(componentTreeHolder.mLayoutSyncCalled);
    Assert.assertEquals(200, size.width);

    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    for (int i = 1; i <= rangeTotal; i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      assertTrue(componentTreeHolder.isTreeValid());
      assertTrue(componentTreeHolder.mLayoutAsyncCalled);

      final int expectedWidth = i % 3 == 0 ? 200 : 100;
      Assert.assertEquals(expectedWidth, componentTreeHolder.mChildWidth);
      Assert.assertEquals(100, componentTreeHolder.mChildHeight);
    }
  }

  @Test
  public void testAddItemsAfterMeasuring() {
    final Size size = new Size();
    final int widthSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);
    final int heightSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);

    mRecyclerBinder.measure(size, widthSpec, heightSpec);

    Assert.assertEquals(200, size.width);

    final List<ComponentInfo> components = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      components.add(ComponentInfo.create().component(mock(Component.class)).build());
      mRecyclerBinder.insertItemAt(i, components.get(i));
    }

    for (int i = 0; i < 100; i++) {
      Assert.assertNotNull(mHoldersForComponents.get(components.get(i).getComponent()));
    }

    TestComponentTreeHolder componentTreeHolder;

    int rangeTotal = RANGE_SIZE + (int) (RANGE_SIZE * RANGE_RATIO);

    for (int i = 0; i < RANGE_SIZE; i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      assertTrue(componentTreeHolder.isTreeValid());
      assertTrue(componentTreeHolder.mLayoutSyncCalled);
    }

    for (int i = RANGE_SIZE; i <= rangeTotal; i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      assertTrue(componentTreeHolder.isTreeValid());
      assertTrue(componentTreeHolder.mLayoutAsyncCalled);
      assertFalse(componentTreeHolder.mLayoutSyncCalled);
    }

    for (int i = rangeTotal + 1; i < components.size(); i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      assertFalse(componentTreeHolder.isTreeValid());
      assertFalse(componentTreeHolder.mLayoutAsyncCalled);
      assertFalse(componentTreeHolder.mLayoutSyncCalled);
    }
  }

  @Test
  public void testRangeBiggerThanContent() {
    final List<ComponentInfo> components = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      components.add(ComponentInfo.create().component(mock(Component.class)).build());
      mRecyclerBinder.insertItemAt(i, components.get(i));
    }

    for (int i = 0; i < 2; i++) {
      Assert.assertNotNull(mHoldersForComponents.get(components.get(i).getComponent()));
    }

    Size size = new Size();
    int widthSpec = SizeSpec.makeSizeSpec(200, SizeSpec.AT_MOST);
    int heightSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);

    mRecyclerBinder.measure(size, widthSpec, heightSpec);

    TestComponentTreeHolder componentTreeHolder =
        mHoldersForComponents.get(components.get(0).getComponent());

    assertTrue(componentTreeHolder.isTreeValid());
    assertTrue(componentTreeHolder.mLayoutSyncCalled);

    for (int i = 1; i < 2; i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      assertTrue(componentTreeHolder.isTreeValid());
      assertTrue(componentTreeHolder.mLayoutAsyncCalled);
      assertFalse(componentTreeHolder.mLayoutSyncCalled);
    }

    Assert.assertEquals(100, size.width);
  }

  @Test
  public void testMoveRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final int newRangeStart = 40;
    final int newRangeEnd = 42;
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    mRecyclerBinder.onNewVisibleRange(newRangeStart, newRangeEnd);

    TestComponentTreeHolder componentTreeHolder;
    for (int i = 0; i < components.size(); i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      if (i >= newRangeStart - (RANGE_RATIO * RANGE_SIZE) && i <= newRangeStart + rangeTotal) {
        assertTrue(componentTreeHolder.isTreeValid());
        assertTrue(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
      } else {
        assertFalse(componentTreeHolder.isTreeValid());
        assertFalse(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
        if (i <= rangeTotal) {
          assertTrue(componentTreeHolder.mDidAcquireStateHandler);
        } else {
          assertFalse(componentTreeHolder.mDidAcquireStateHandler);
        }
      }
    }
  }

  @Test
  public void testRealRangeOverridesEstimatedRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final int newRangeStart = 40;
    final int newRangeEnd = 50;
    int rangeSize = newRangeEnd - newRangeStart;
    final int rangeTotal = (int) (rangeSize + (RANGE_RATIO * rangeSize));

    mRecyclerBinder.onNewVisibleRange(newRangeStart, newRangeEnd);

    TestComponentTreeHolder componentTreeHolder;
    for (int i = 0; i < components.size(); i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      if (i >= newRangeStart - (RANGE_RATIO * rangeSize) && i <= newRangeStart + rangeTotal) {
        assertTrue(componentTreeHolder.isTreeValid());
        assertTrue(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
      } else {
        assertFalse(componentTreeHolder.isTreeValid());
        assertFalse(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
      }
    }
  }

  @Test
  public void testStickyComponentsStayValidOutsideRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    makeIndexSticky(components, 5);
    makeIndexSticky(components, 40);
    makeIndexSticky(components, 80);

    Size size = new Size();
    int widthSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);
    int heightSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);

    mRecyclerBinder.measure(size, widthSpec, heightSpec);

    assertTrue(mHoldersForComponents.get(components.get(5).getComponent()).isTreeValid());

    final int newRangeStart = 40;
    final int newRangeEnd = 50;
    int rangeSize = newRangeEnd - newRangeStart;
    final int rangeTotal = (int) (rangeSize + (RANGE_RATIO * rangeSize));

    mRecyclerBinder.onNewVisibleRange(newRangeStart, newRangeEnd);

    TestComponentTreeHolder componentTreeHolder;
    for (int i = 0; i < components.size(); i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());
      boolean isIndexInRange =
          i >= newRangeStart - (RANGE_RATIO * rangeSize) && i <= newRangeStart + rangeTotal;
      boolean isPreviouslyComputedTreeAndSticky =
          i <= newRangeStart + rangeTotal && componentTreeHolder.getComponentInfo().isSticky();

      if (isIndexInRange || isPreviouslyComputedTreeAndSticky) {
        assertTrue(componentTreeHolder.isTreeValid());
        assertTrue(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
      } else {
        assertFalse(componentTreeHolder.isTreeValid());
        assertFalse(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
      }
    }
  }

  @Test
  public void testMoveRangeToEnd() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final int newRangeStart = 99;
    final int newRangeEnd = 99;
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    mRecyclerBinder.onNewVisibleRange(newRangeStart, newRangeEnd);

    TestComponentTreeHolder componentTreeHolder;

    for (int i = 0; i < components.size(); i++) {
      componentTreeHolder = mHoldersForComponents.get(components.get(i).getComponent());

      if (i >= newRangeStart - (RANGE_RATIO * RANGE_SIZE) && i <= newRangeStart + rangeTotal) {
        assertTrue(componentTreeHolder.isTreeValid());
        assertTrue(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
      } else {
        assertFalse(componentTreeHolder.isTreeValid());
        assertFalse(componentTreeHolder.mLayoutAsyncCalled);
        assertFalse(componentTreeHolder.mLayoutSyncCalled);
        if (i <= rangeTotal) {
          assertTrue(componentTreeHolder.mDidAcquireStateHandler);
        } else {
          assertFalse(componentTreeHolder.mDidAcquireStateHandler);
        }
      }
    }
  }

  @Test
  public void testMoveItemOutsideFromRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    mRecyclerBinder.moveItem(0, 99);

    final TestComponentTreeHolder movedHolder =
        mHoldersForComponents.get(components.get(0).getComponent());
    assertFalse(movedHolder.isTreeValid());
    assertFalse(movedHolder.mLayoutAsyncCalled);
    assertFalse(movedHolder.mLayoutSyncCalled);
    assertTrue(movedHolder.mDidAcquireStateHandler);

    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));
    final TestComponentTreeHolder holderMovedIntoRange =
        mHoldersForComponents.get(components.get(rangeTotal + 1).getComponent());

    assertTrue(holderMovedIntoRange.isTreeValid());
    assertTrue(holderMovedIntoRange.mLayoutAsyncCalled);
    assertFalse(holderMovedIntoRange.mLayoutSyncCalled);
  }

  @Test
  public void testMoveItemInsideRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    mRecyclerBinder.moveItem(99, 4);

    TestComponentTreeHolder movedHolder =
        mHoldersForComponents.get(components.get(99).getComponent());
    assertTrue(movedHolder.isTreeValid());
    assertTrue(movedHolder.mLayoutAsyncCalled);
    assertFalse(movedHolder.mLayoutSyncCalled);
    assertFalse(movedHolder.mDidAcquireStateHandler);
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    TestComponentTreeHolder holderMovedOutsideRange =
        mHoldersForComponents.get(components.get(rangeTotal).getComponent());

    assertFalse(holderMovedOutsideRange.isTreeValid());
    assertFalse(holderMovedOutsideRange.mLayoutAsyncCalled);
    assertFalse(holderMovedOutsideRange.mLayoutSyncCalled);
  }

  @Test
  public void testMoveItemInsideVisibleRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    mRecyclerBinder.moveItem(99, 2);

    final TestComponentTreeHolder movedHolder =
        mHoldersForComponents.get(components.get(99).getComponent());

    assertTrue(movedHolder.isTreeValid());
    assertFalse(movedHolder.mLayoutAsyncCalled);
    assertTrue(movedHolder.mLayoutSyncCalled);
    assertFalse(movedHolder.mDidAcquireStateHandler);
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    final TestComponentTreeHolder holderMovedOutsideRange =
        mHoldersForComponents.get(components.get(rangeTotal).getComponent());

    assertFalse(holderMovedOutsideRange.isTreeValid());
    assertFalse(holderMovedOutsideRange.mLayoutAsyncCalled);
    assertFalse(holderMovedOutsideRange.mLayoutSyncCalled);
    assertTrue(holderMovedOutsideRange.mDidAcquireStateHandler);
  }

  @Test
  public void testMoveItemOutsideRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    mRecyclerBinder.moveItem(2, 99);

    final TestComponentTreeHolder movedHolder =
        mHoldersForComponents.get(components.get(2).getComponent());
    assertFalse(movedHolder.isTreeValid());
    assertFalse(movedHolder.mLayoutAsyncCalled);
    assertFalse(movedHolder.mLayoutSyncCalled);
    assertTrue(movedHolder.mDidAcquireStateHandler);

    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));
    final TestComponentTreeHolder holderMovedInsideRange =
        mHoldersForComponents.get(components.get(rangeTotal + 1).getComponent());

    assertTrue(holderMovedInsideRange.isTreeValid());
    assertTrue(holderMovedInsideRange.mLayoutAsyncCalled);
    assertFalse(holderMovedInsideRange.mLayoutSyncCalled);
    assertFalse(holderMovedInsideRange.mDidAcquireStateHandler);
  }

  @Test
  public void testMoveWithinRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();

    final TestComponentTreeHolder movedHolderOne =
        mHoldersForComponents.get(components.get(0).getComponent());
    final TestComponentTreeHolder movedHolderTwo =
        mHoldersForComponents.get(components.get(1).getComponent());

    movedHolderOne.mLayoutSyncCalled = false;
    movedHolderOne.mLayoutAsyncCalled = false;
    movedHolderOne.mDidAcquireStateHandler = false;

    movedHolderTwo.mLayoutSyncCalled = false;
    movedHolderTwo.mLayoutAsyncCalled = false;
    movedHolderTwo.mDidAcquireStateHandler = false;

    mRecyclerBinder.moveItem(0, 1);

    assertTrue(movedHolderOne.isTreeValid());
    assertFalse(movedHolderOne.mLayoutAsyncCalled);
    assertFalse(movedHolderOne.mLayoutSyncCalled);
    assertFalse(movedHolderOne.mDidAcquireStateHandler);

    assertTrue(movedHolderTwo.isTreeValid());
    assertFalse(movedHolderTwo.mLayoutAsyncCalled);
    assertFalse(movedHolderTwo.mLayoutSyncCalled);
    assertFalse(movedHolderTwo.mDidAcquireStateHandler);
  }

  @Test
  public void testInsertInVisibleRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final ComponentInfo newComponentInfo =
        ComponentInfo.create().component(mock(Component.class)).build();

    mRecyclerBinder.insertItemAt(1, newComponentInfo);
    final TestComponentTreeHolder holder =
        mHoldersForComponents.get(newComponentInfo.getComponent());

    assertTrue(holder.isTreeValid());
    assertTrue(holder.mLayoutSyncCalled);
    assertFalse(holder.mLayoutAsyncCalled);
    assertFalse(holder.mDidAcquireStateHandler);

    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));
    final TestComponentTreeHolder holderMovedOutsideRange =
        mHoldersForComponents.get(components.get(rangeTotal).getComponent());

    assertFalse(holderMovedOutsideRange.isTreeValid());
    assertFalse(holderMovedOutsideRange.mLayoutSyncCalled);
    assertFalse(holderMovedOutsideRange.mLayoutAsyncCalled);
    assertTrue(holderMovedOutsideRange.mDidAcquireStateHandler);
  }

  @Test
  public void testInsertInRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final ComponentInfo newComponentInfo =
        ComponentInfo.create().component(mock(Component.class)).build();

    mRecyclerBinder.insertItemAt(RANGE_SIZE + 1, newComponentInfo);
    final TestComponentTreeHolder holder =
        mHoldersForComponents.get(newComponentInfo.getComponent());

    assertTrue(holder.isTreeValid());
    assertFalse(holder.mLayoutSyncCalled);
    assertTrue(holder.mLayoutAsyncCalled);
    assertFalse(holder.mDidAcquireStateHandler);

    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));
    final TestComponentTreeHolder holderMovedOutsideRange =
        mHoldersForComponents.get(components.get(rangeTotal).getComponent());

    assertFalse(holderMovedOutsideRange.isTreeValid());
    assertFalse(holderMovedOutsideRange.mLayoutSyncCalled);
    assertFalse(holderMovedOutsideRange.mLayoutAsyncCalled);
    assertTrue(holderMovedOutsideRange.mDidAcquireStateHandler);
  }

  @Test
  public void testInsertRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final List<ComponentInfo> newComponents = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      newComponents.add(
          ComponentInfo.create().component(mock(Component.class)).build());
    }

    mRecyclerBinder.insertRangeAt(0, newComponents);

    // The new elements were scheduled for layout.
    for (int i = 0; i < 3; i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(newComponents.get(i).getComponent());
      assertTrue(holder.isTreeValid());
      assertFalse(holder.mLayoutSyncCalled);
      assertTrue(holder.mLayoutAsyncCalled);
      assertFalse(holder.mDidAcquireStateHandler);
    }

    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    // The elements that went outside the layout range have been released.
    for (int i = rangeTotal - 2; i <= rangeTotal; i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      assertFalse(holder.isTreeValid());
      assertFalse(holder.mLayoutSyncCalled);
      assertFalse(holder.mLayoutAsyncCalled);
      assertTrue(holder.mDidAcquireStateHandler);
    }
  }

  @Test
  public void testInsertOusideRange() {
    prepareLoadedBinder();
    final ComponentInfo newComponentInfo =
        ComponentInfo.create().component(mock(Component.class)).build();
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    mRecyclerBinder.insertItemAt(rangeTotal + 1, newComponentInfo);

    final TestComponentTreeHolder holder =
        mHoldersForComponents.get(newComponentInfo.getComponent());

    assertFalse(holder.isTreeValid());
    assertFalse(holder.mLayoutSyncCalled);
    assertFalse(holder.mLayoutAsyncCalled);
    assertFalse(holder.mDidAcquireStateHandler);
  }

  @Test
  public void testRemoveItem() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    mRecyclerBinder.removeItemAt(rangeTotal + 1);

    final TestComponentTreeHolder holder =
        mHoldersForComponents.get(components.get(rangeTotal + 1).getComponent());
    assertTrue(holder.mReleased);
  }

  @Test
  public void testRemoveFromRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    mRecyclerBinder.removeItemAt(rangeTotal);

    final TestComponentTreeHolder holder =
        mHoldersForComponents.get(components.get(rangeTotal).getComponent());
    assertTrue(holder.mReleased);

    final TestComponentTreeHolder holderMovedInRange =
        mHoldersForComponents.get(components.get(rangeTotal + 1).getComponent());
    assertTrue(holderMovedInRange.isTreeValid());
    assertFalse(holderMovedInRange.mLayoutSyncCalled);
    assertTrue(holderMovedInRange.mLayoutAsyncCalled);
    assertFalse(holderMovedInRange.mDidAcquireStateHandler);
  }

  @Test
  public void testRemoveRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();
    final int rangeTotal = (int) (RANGE_SIZE + (RANGE_RATIO * RANGE_SIZE));

    mRecyclerBinder.removeRangeAt(0, RANGE_SIZE);

    // The elements that were removed have been released.
    for (int i = 0; i < RANGE_SIZE; i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      assertTrue(holder.mReleased);
    }

    // The elements that are now in the range get their layout computed
    for (int i = rangeTotal + 1; i <= rangeTotal + RANGE_SIZE; i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      assertTrue(holder.isTreeValid());
      assertFalse(holder.mLayoutSyncCalled);
      assertTrue(holder.mLayoutAsyncCalled);
      assertFalse(holder.mDidAcquireStateHandler);
    }
  }

  @Test
  public void testUpdate() {
    final List<ComponentInfo> components = prepareLoadedBinder();

    final TestComponentTreeHolder holder =
        mHoldersForComponents.get(components.get(0).getComponent());
    assertTrue(holder.isTreeValid());
    holder.mTreeValid = false;
    assertFalse(holder.isTreeValid());

    final ComponentInfo newComponentInfo =
        ComponentInfo.create().component(mock(Component.class)).build();
    mRecyclerBinder.updateItemAt(0, newComponentInfo);

    assertEquals(holder.getComponentInfo(), newComponentInfo);
    assertTrue(holder.isTreeValid());
  }

  @Test
  public void testUpdateRange() {
    final List<ComponentInfo> components = prepareLoadedBinder();

    for (int i = 0; i < RANGE_SIZE; i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      assertTrue(holder.isTreeValid());
      holder.mTreeValid = false;
      assertFalse(holder.isTreeValid());
    }

    final List<ComponentInfo> newInfos = new ArrayList<>();
    for (int i = 0; i < RANGE_SIZE; i++) {
      newInfos.add(
          ComponentInfo.create().component(mock(Component.class)).build());
    }
    mRecyclerBinder.updateRangeAt(0, newInfos);

    for (int i = 0; i < RANGE_SIZE; i++) {
      final TestComponentTreeHolder holder =
          mHoldersForComponents.get(components.get(i).getComponent());
      assertEquals(holder.getComponentInfo(), newInfos.get(i));
      assertTrue(holder.isTreeValid());
    }
  }

  @Test
  public void testGetItemCount() {
    for (int i = 0; i < 100; i++) {
      assertEquals(i, mRecyclerBinder.getItemCount());
      mRecyclerBinder.insertItemAt(
          i,
          ComponentInfo.create().component(mock(Component.class)).build());
    }
  }

  private List<ComponentInfo> prepareLoadedBinder() {
    final List<ComponentInfo> components = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      components.add(ComponentInfo.create().component(mock(Component.class)).build());
      mRecyclerBinder.insertItemAt(i, components.get(i));
    }

    for (int i = 0; i < 100; i++) {
      Assert.assertNotNull(mHoldersForComponents.get(components.get(i).getComponent()));
    }

    Size size = new Size();
    int widthSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);
    int heightSpec = SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY);

    mRecyclerBinder.measure(size, widthSpec, heightSpec);

    return components;
  }

  private void makeIndexSticky(List<ComponentInfo> components, int i) {
    components.set(
        i,
        ComponentInfo.create().component(mock(Component.class)).isSticky(true).build());
    mRecyclerBinder.removeItemAt(i);
    mRecyclerBinder.insertItemAt(i, components.get(i));
  }

  private static class TestComponentTreeHolder extends ComponentTreeHolder {

    private boolean mTreeValid;
    private ComponentTree mComponentTree;
    private ComponentInfo mComponentInfo;
    private boolean mLayoutAsyncCalled;
    private boolean mLayoutSyncCalled;
    private boolean mDidAcquireStateHandler;
    private boolean mReleased;
    private int mChildWidth;
    private int mChildHeight;

    private TestComponentTreeHolder(ComponentInfo componentInfo) {
      mComponentInfo = componentInfo;
    }

    @Override
    protected void release() {
      mReleased = true;
    }

    @Override
    protected synchronized void acquireStateHandlerAndReleaseTree() {
      mComponentTree = null;
      mTreeValid = false;
      mLayoutAsyncCalled = false;
      mLayoutSyncCalled = false;
      mDidAcquireStateHandler = true;
    }

    @Override
    protected synchronized void invalidateTree() {
      mTreeValid = false;
      mLayoutAsyncCalled = false;
      mLayoutSyncCalled = false;
    }

    @Override
    protected synchronized void computeLayoutAsync(
        ComponentContext context,
        int widthSpec,
        int heightSpec) {
      mComponentTree = mock(ComponentTree.class);
      mTreeValid = true;
      mLayoutAsyncCalled = true;
      mChildWidth = SizeSpec.getSize(widthSpec);
      mChildHeight = SizeSpec.getSize(heightSpec);
    }

    @Override
    protected void computeLayoutSync(
        ComponentContext context, int widthSpec, int heightSpec, Size size) {
      mComponentTree = mock(ComponentTree.class);
      mTreeValid = true;
      if (size != null) {
        size.width = SizeSpec.getSize(widthSpec);
        size.height = SizeSpec.getSize(heightSpec);
      }

      mLayoutSyncCalled = true;
    }

    @Override
    public void setComponentInfo(ComponentInfo componentInfo) {
      mComponentInfo = componentInfo;
    }

    @Override
    protected synchronized boolean isTreeValid() {
      return mTreeValid;
    }

    @Override
    protected synchronized ComponentTree getComponentTree() {
      return mComponentTree;
    }

    @Override
    public ComponentInfo getComponentInfo() {
      return mComponentInfo;
    }
  }
}
