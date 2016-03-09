package minderengine;

import org.interop.xoola.core.XoolaProperty;

import java.util.HashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This class has instances per session. It holds the values of signal calls that have occurred on the wrappers' side.
 * <p>
 * Created by yerlibilgin on 02/12/14.
 */
public class MinderSignalRegistry {

  private static MinderSignalRegistry instance;

  public static MinderSignalRegistry get() {
    if (instance == null) {
      instance = new MinderSignalRegistry();
    }

    return instance;
  }

  /*
  * HashMap<AdapterIdentifier,HashMap<TestSession, HasMap<signatureofSignal, signalqueue>>>>
  * */
  private HashMap<TestSession, HashMap<AdapterIdentifier, HashMap<String, PriorityBlockingQueue<SignalData>>>> testSessionMap = new HashMap<TestSession, HashMap<AdapterIdentifier, HashMap<String, PriorityBlockingQueue<SignalData>>>>();

  /**
   * A signal was emitted on the wrapper side. We should put it into the queue until it gets taken by a rivet.
   *
   * @param adapterIdentifier
   * @param signature
   * @param signalData
   */
  public void enqueueSignal(TestSession testSession, AdapterIdentifier adapterIdentifier, String signature, SignalData signalData) {
    PriorityBlockingQueue<SignalData> signalQueue = findRelatedQueue(testSession, adapterIdentifier, signature);
    signalQueue.offer(signalData);
  }

  /**
   * If the signal is not yet emitted, we still have to settle down
   * and wait on a queue. That is why, we have to call findRelatedQueue method here too
   *
   * @param adapterIdentifier
   * @param signature
   * @param timeout           maximum timeout to wait for the deque operation. 0 means get the default (XoolaProperty.NETWORK_RESPONSE_TIMEOUT)
   *                          from app.conf.
   * @return
   */
  public SignalData dequeueSignal(TestSession testSession, AdapterIdentifier adapterIdentifier, String signature, long timeout) {
    PriorityBlockingQueue<SignalData> signalQueue = findRelatedQueue(testSession, adapterIdentifier, signature);

    if (timeout == 0)
      timeout = Long.parseLong(XoolaServer.properties.getProperty(XoolaProperty.NETWORK_RESPONSE_TIMEOUT));

    try {
      SignalData result = signalQueue.poll(timeout, TimeUnit.MILLISECONDS);
      if (result == null) {
        throw new RuntimeException("Signal Timeout Expired (" + timeout + ")");
      }

      return result;
    } catch (InterruptedException e) {
      throw new RuntimeException("Signal dequeue operation cancelled");
    }
  }

  /*
  * Adding an incoming signal into the related queue.
  * */
  private PriorityBlockingQueue<SignalData> findRelatedQueue(TestSession testSession, AdapterIdentifier adapterIdentifier, String signature) {
    HashMap<AdapterIdentifier, HashMap<String, PriorityBlockingQueue<SignalData>>> adapterMap = null;
    if (!testSessionMap.containsKey(testSession)) {
      adapterMap = new HashMap<AdapterIdentifier, HashMap<String, PriorityBlockingQueue<SignalData>>>();
      testSessionMap.put(testSession, adapteMap);
    } else {
      adapterMap = testSessionMap.get(testSession);
    }

    HashMap<String, PriorityBlockingQueue<SignalData>> signalMap = null;
    if (!adapterMap.containsKey(adapterIdentifier)) {
      signalMap = new HashMap<String, PriorityBlockingQueue<SignalData>>();
      adapterMap.put(adapterIdentifier,signalMap);
    } else {
      signalMap = adapterMap.get(adapterIdentifier);
    }

    PriorityBlockingQueue<SignalData> queue = null;
    if (!signalMap.containsKey(signature)) {
      queue = new PriorityBlockingQueue<SignalData>();
      signalMap.put(signature, queue);
    } else {
      queue = signalMap.get(signature);
    }
    return queue;
  }

  /**
   * Initialize the necessary data structures for a test session
   * @param testSession
   */
  public void initTestSession(TestSession testSession) {
    testSessionMap.put(testSession, new HashMap<>());
  }

  /**
   * Remove the map for that specific test session
   * @param testSession
   */
  public void purgeTestSession(TestSession testSession) {
    testSessionMap.remove(testSession);
  }

  public boolean hasSession(TestSession testSession){
    return testSessionMap.containsKey(testSession);
  }

}
