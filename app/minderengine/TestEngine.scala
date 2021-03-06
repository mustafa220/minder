package minderengine

import java.io.ByteArrayInputStream
import java.lang.reflect.InvocationTargetException
import java.util
import java.util.Properties
import javax.inject.{Inject, Provider, Singleton}

import com.gitb.core.v1.StepStatus
import controllers.{TestQueueController, TestRunContext}
import utils.Util
import models._
import mtdl._
import org.apache.log4j.spi.LoggingEvent
import org.apache.log4j.{AppenderSkeleton, EnhancedPatternLayout, Level}

import scala.collection.JavaConversions._
import scala.collection.mutable;

/**
  * Created by yerlibilgin on 07/12/14.
  */
@Singleton
class TestEngine @Inject()(implicit testQueueController: Provider[TestQueueController],
                           xoolaServer: Provider[XoolaServer]) {
  var minderTDL: MinderTdl = null
  var suspendedTestsMap = new mutable.HashMap[String, TestRunContext]();

  /**
    * When Xoola server is initialized, it needs a class loader to deserialize the objects read from the network.
    * We will provide xoola the minderTDL.getClass.getClassLoader so that it successfully resolves its stuff.
    * This function will delegate the minderTDL.getClass.getClassLoader to xoola.
    */

  def getCurrentMTDLClassLoader(): ClassLoader = {
    if (minderTDL == null)
      Thread.currentThread().getContextClassLoader;
    else
      minderTDL.getClass.getClassLoader
  }

  class MyAppender(testProcessWatcher: TestProcessWatcher) extends AppenderSkeleton {
    override def append(event: LoggingEvent): Unit = {
      if (this.getLayout != null) {
        val formatted = this.getLayout.format(event);

        if (event.getLevel.toInt == Level.INFO.toInt || event.getLevel.toInt == Level.ERROR.toInt) {
          testProcessWatcher.addReportLog(formatted);
        } else {
          testProcessWatcher.addLog(formatted);
        }
      }
    }

    def close() {}

    def requiresLayout(): Boolean = {
      false;
    }
  }

  class GitbWatcher() extends GitbRivetWatcher {
    override def notifyProcessingInfo(log: String, rivet: Rivet): Unit = {
      notify(log, StepStatus.PROCESSING, rivet)
    }

    override def notifySkippedInfo(log: String, rivet: Rivet): Unit = {
      notify(log, StepStatus.SKIPPED, rivet)
    }

    override def notifyWaitingInfo(log: String, rivet: Rivet): Unit = {
      notify(log, StepStatus.WAITING, rivet)
    }

    override def notifyErrorInfo(log: String, rivet: Rivet): Unit = {
      notify(log, StepStatus.ERROR, rivet)
    }

    override def notifyCompletedInfo(log: String, rivet: Rivet): Unit = {
      notify(log, StepStatus.COMPLETED, rivet)
    }

    def notify(log: String, stepStatus: StepStatus, rivet: Rivet): Unit = {
      testQueueController.get().gitbLogFeedUpdate(log, stepStatus, rivet.tplStepId)
    }
  }

  /**
    * Runs the provided already compiled tdl class with the given parameter mapping
    *
    * @param userEmail
    * @param clsMinderTDL
    * @param wrapperMapping
    * @param testRunContext
    */
  def runTest(sessionID: String, userEmail: String, clsMinderTDL: Class[MinderTdl],
              wrapperMapping: collection.mutable.Map[String, MappedWrapper],
              testRunContext: TestRunContext, params: String): Unit = {
    val lgr: org.apache.log4j.Logger = org.apache.log4j.Logger.getLogger("test");
    val app = new MyAppender(testRunContext);
    val gtb = new GitbWatcher();
    app.setLayout(new EnhancedPatternLayout("%d{ISO8601}: %-5p - %m%n%throwable"));
    try {
      lgr.addAppender(app);

      lgr.info(s"Minder version ${Util.getVersionInfo()}")
      if (testRunContext.isSuspended()) {
        lgr.info("Resume Test")
      } else {
        lgr.info("Start Test")
      }
      var identifierMinderClientMap: util.Map[AdapterIdentifier, IdentifierClientPair] = null

      val session = new TestSession(sessionID)

      try {
        lgr.info("Initialize test case")

        minderTDL = testRunContext.initialize();

        identifierMinderClientMap = mapAllTransitiveWrappers(wrapperMapping)

        initializeFunctionsAndParameters(params, lgr)

        if (!testRunContext.isSuspended()) {
          val sutNameSet = new util.HashSet[String]

          //first, call the start methods for all registered wrappers of this test.

          lgr.info("> Initialize the Adapters");

          val startTestObject = new StartTestObject


          val properties = new Properties()
          properties.load(new ByteArrayInputStream(params.getBytes()))


          startTestObject.setSession(session)
          startTestObject.setProperties(properties)


          //call start test on all adapters and populate the SUT names
          identifierMinderClientMap.values().foreach(pair => {
            lgr.info("Call start test on " + pair.adapterIdentifier.getName)
            pair.minderClient.startTest(startTestObject)

            lgr.info("Obtain SUT Identifiers from " + pair.adapterIdentifier.getName)
            val sutIdentifiers = pair.minderClient.getSUTIdentifiers;

            if (sutIdentifiers != null) {
              for (sutIdentifier <- sutIdentifiers.getIdentifiers) {
                lgr.info("System Under Test: " + sutIdentifier.getSutName)
                sutNameSet.add(sutIdentifier.getSutName)
              }
            }
          })


          //update the SUT names on the watcher
          testRunContext.updateSUTNames(sutNameSet)
        }

        while (minderTDL.currentRivetIndex < minderTDL.RivetDefs.size()) {
          val currentRivet = minderTDL.RivetDefs(minderTDL.currentRivetIndex)
          //for (currentRivet <- minderTDL.RivetDefs) {
          var msg: String = "> RUN RIVET " + minderTDL.currentRivetIndex;
          lgr.info(msg)
          gtb.notifyProcessingInfo(msg, currentRivet)

          if (currentRivet.isInstanceOf[Suspend]) {
            //save testRunContext in a map
            minderTDL.currentRivetIndex += 1;
            minderengine.ContextContainer.get().addTestContext(session, testRunContext);
            lgr.info("Suspend Test Case")
            testRunContext.suspend()
            return
          } else {

            val rivetWrapperId: String = currentRivet.wrapperFunction.wrapperId
            //resolve the minder client id. This might as well be resolved to a local built-in wrapper or the null slot.
            val slotPair = findPairOrError(identifierMinderClientMap, AdapterIdentifier.parse(rivetWrapperId))
            val args = Array.ofDim[Object](currentRivet.pipes.length)

            //a boolean flag used to see whether a timeout occurred or not
            var thereIsTimoeut = false;

            var signalIndex = 0

            try {
              for (tuple@(label, signature) <- currentRivet.signalPipeMap.keySet) {

                if (!MinderSignalRegistry.get().hasSession(session)) {
                  msg = "No MinderSignalRegistry object defined for session " + userEmail;
                  gtb.notifyErrorInfo(msg, currentRivet)
                  throw new scala.IllegalArgumentException(msg)
                }

                //obtain the source signal object
                val signalList = currentRivet.signalPipeMap(tuple);
                if (signalList == null || signalList.isEmpty) {
                  msg = "singal list is empty for " + label + "." + signature;
                  gtb.notifyErrorInfo(msg, currentRivet);
                  throw new scala.IllegalArgumentException(msg);
                }

                val signal = signalList(0).inRef.source;

                val signalPair = findPairOrError(identifierMinderClientMap, AdapterIdentifier.parse(label));

                val signalAdapterIdentifier = signalPair.adapterIdentifier
                msg = "> Wait For Signal:" + signalAdapterIdentifier + "." + signature;
                lgr.debug(msg)
                gtb.notifyWaitingInfo(msg, currentRivet)


                val signalData: SignalData = try {
                  MinderSignalRegistry.get().dequeueSignal(session, signalAdapterIdentifier, signature, signal.timeout)
                } catch {
                  case rte: RuntimeException => {
                    signal.handleTimeout(rte)
                    thereIsTimoeut = true
                    gtb.notifyErrorInfo("Break currentRivet", currentRivet)
                    throw new BreakException
                  }
                }

                msg = "< Signal Arrived: " + signalAdapterIdentifier + "." + signature
                lgr.debug(msg)
                gtb.notifyProcessingInfo(msg, currentRivet)

                if (signalData.isInstanceOf[SignalErrorData]) {
                  lgr.debug("This is an error signal");
                  val signalErrorData = signalData.asInstanceOf[SignalErrorData]
                  msg = "Signal [" + signalAdapterIdentifier + "." + signature + "] failed [" + signalErrorData.signalFailedException.getMessage
                  gtb.notifyErrorInfo(msg, currentRivet)
                  throw new scala.RuntimeException(msg, signalErrorData.signalFailedException)
                }
                val signalCallData: SignalCallData = signalData.asInstanceOf[SignalCallData]

                testRunContext.signalEmitted(minderTDL.currentRivetIndex, signalIndex, signalCallData)

                for (paramPipe <- currentRivet.signalPipeMap(tuple)) {
                  //FIX for BUG-1 : added an if for -1 param
                  if (paramPipe.in != -1) {
                    val arg = Util.readObject(signalCallData.args(paramPipe.in), getCurrentMTDLClassLoader())
                    convertParam(paramPipe.out, paramPipe.execute(arg), args)
                  }
                }
                signalIndex += 1
              }
            } catch {
              case breakException: BreakException => {
              }
            }

            if (thereIsTimoeut) {
              //we hit a timeout, skip to the next currentRivet
              gtb.notifySkippedInfo("< Rivet skipped", currentRivet)
            } else {
              lgr.debug("Assign free vars")

              for (paramPipe <- currentRivet.freeVariablePipes) {
                val any = paramPipe.execute(null)
                convertParam(paramPipe.out, any, args)
              }

              if (currentRivet.freeVariablePipes.size > 0) {
                val freeArgs = Array.ofDim[Object](currentRivet.freeVariablePipes.size);
                var k = 0;
                for (paramPipe <- currentRivet.freeVariablePipes) {
                  freeArgs(k) = args(paramPipe.out)
                  k += 1
                }
                val signalData2 = new SignalCallData(freeArgs);
                testRunContext.signalEmitted(minderTDL.currentRivetIndex, signalIndex, signalData2)
              }

              msg = "> CALL SLOT " + slotPair.adapterIdentifier + "." + currentRivet.wrapperFunction.signature
              lgr.info(msg)
              gtb.notifyProcessingInfo(msg, currentRivet)
              testRunContext.rivetInvoked(minderTDL.currentRivetIndex);
              currentRivet.result = slotPair.minderClient.callSlot(session, currentRivet.wrapperFunction.signature, args)
              msg = "< SLOT CALLED " + slotPair.adapterIdentifier + "." + currentRivet.wrapperFunction.signature;
              lgr.info(msg)
              gtb.notifyProcessingInfo(msg, currentRivet)


              msg = "< Rivet finished sucessfully";
              testRunContext.rivetFinished(minderTDL.currentRivetIndex)
              gtb.notifyCompletedInfo(msg, currentRivet)
              lgr.info(msg)
              lgr.info("----------\n")

            }


            if (Thread.currentThread().isInterrupted) throw new scala.InterruptedException("Test interrupted")
          }
          minderTDL.currentRivetIndex += 1;
        }

        if (minderTDL.exception != null) {
          throw minderTDL.exception
        } else {
          lgr.info(s"Test #${testRunContext.testRun.number} Finished")
          testRunContext.finished()
        }
      } catch {
        case t: Throwable => {
          val error = {
            var cause = t;
            var err: String = null;
            while (cause.getCause != null) {
              cause = cause.getCause
            }

            err = cause.getMessage

            if (err == null) {
              err = "Unknown error [" + t.getClass.getName + "]";
            }
            err
          }
          lgr.error(s"Test #${testRunContext.testRun.number} Failed")
          lgr.error(error, t)
          testRunContext.failed(error, t);
        }
      } finally {
        if (testRunContext.isSuspended()) {
          lgr.info("> Test Suspended")
        } else {
          lgr.info("> Send finish message to all wrappers")
          //make sure that we call finish test for all
          val finishTestObject: FinishTestObject = new FinishTestObject
          finishTestObject.setSession(session)
          try {
            identifierMinderClientMap.values().foreach(pair => {
              pair.minderClient.finishTest(finishTestObject)
            })
          } catch {
            case th: Throwable =>
          }
        }
      }
    }
    finally {
      lgr.removeAppender(app)
    }
  }

  private def initializeFunctionsAndParameters(params: String, lgr: org.apache.log4j.Logger): Unit = {
    lgr.debug("Parameters")
    lgr.debug(params)
    minderTDL.debug = (any: Any) => {
      lgr.debug(any)
    }
    minderTDL.debugThrowable = (any: Any, th: Throwable) => {
      lgr.debug(any, th)
    }
    minderTDL.info = (any: Any) => {
      lgr.info(any)
    }
    minderTDL.infoThrowable = (any: Any, th: Throwable) => {
      lgr.info(any, th)
    }
    minderTDL.error = (any: Any) => {
      lgr.error(any)
    }
    minderTDL.errorThrowable = (any: Any, th: Throwable) => {
      lgr.error(any, th)
    }

    minderTDL.setParams(params);
  }

  def convertParam(out: Int, arg: Any, args: Array[Object]) {
    if (arg == null) {
      args(out) = null;
    } else if (arg.isInstanceOf[Rivet]) {
      args(out) = arg.asInstanceOf[Rivet].result
    } else if (arg.isInstanceOf[MinderNull]) {
      args(out) = null;
    } else {
      args(out) = arg.asInstanceOf[AnyRef]
    }
  }


  private def mapAllTransitiveWrappers(wrapperMapping: mutable.Map[String, MappedWrapper]): util.Map[AdapterIdentifier, IdentifierClientPair] = {
    //map all the variable names to actual wrappers, and resolve everything
    val identifierMinderClientMap = new util.LinkedHashMap[AdapterIdentifier, IdentifierClientPair];

    //create NULLSLOT client
    val nullIdentifier = AdapterIdentifier.parse(MinderTdl.NULL_WRAPPER_NAME);
    identifierMinderClientMap.put(nullIdentifier, IdentifierClientPair(nullIdentifier, new NullClient))

    for (wrapperName <- minderTDL.wrapperDefs) {
      val adapterIdentifier = AdapterIdentifier.parse(wrapperName)

      if (adapterIdentifier.getName.startsWith("$")) {
        if (adapterIdentifier.getVersion != null)
          throw new IllegalArgumentException("A variable adapter name cannot have version [" + adapterIdentifier + "]")
        // this is a variable
        //resolve the variable name from the wrapperMapping
        if (!wrapperMapping.contains(adapterIdentifier.getName))
          throw new IllegalArgumentException(adapterIdentifier.getName + " has not been mapped to a real adapter")

        val mapping: MappedWrapper = wrapperMapping(adapterIdentifier.getName)

        val transitiveAdapterIdentifier = AdapterIdentifier.parse(mapping.wrapperVersion.wrapper.name + "|" +
            mapping.wrapperVersion.version)
        val client = if (BuiltInWrapperRegistry.get().contains(transitiveAdapterIdentifier)) {
          BuiltInWrapperRegistry.get().getWrapper(transitiveAdapterIdentifier)
        } else {
          xoolaServer.get().getClient(transitiveAdapterIdentifier);
        }
        identifierMinderClientMap.put(adapterIdentifier, IdentifierClientPair(transitiveAdapterIdentifier, client))
      } else {
        //this is a regular adapter
        //resolve the version
        val version = if (adapterIdentifier.getVersion() == null) {
          //no version supplied, resolve the latest version
          val tmp = WrapperVersion.latestByWrapperName(adapterIdentifier.getName)
          if (tmp == null) {
            throw new IllegalArgumentException(adapterIdentifier.getName + " has not still declared a version")
          }
          tmp
        } else {
          val tmp = WrapperVersion.findWrapperNameAndVersion(adapterIdentifier.getName, adapterIdentifier.getVersion)
          if (tmp == null) {
            throw new IllegalArgumentException(adapterIdentifier + " was not found")
          }
          tmp
        }

        val transitiveAdapterIdentifier = AdapterIdentifier.parse(version.wrapper.name + "|" + version.version)

        val client = if (BuiltInWrapperRegistry.get().contains(transitiveAdapterIdentifier)) {
          BuiltInWrapperRegistry.get().getWrapper(transitiveAdapterIdentifier)
        } else {
          xoolaServer.get().getClient(transitiveAdapterIdentifier);
        }
        identifierMinderClientMap.put(adapterIdentifier, IdentifierClientPair(transitiveAdapterIdentifier, client))
      }

    }
    identifierMinderClientMap
  }

  def findPairOrError(identifierMinderClientMap: util.Map[AdapterIdentifier, IdentifierClientPair], rivetWrapperId: AdapterIdentifier): IdentifierClientPair = {
    if (!identifierMinderClientMap.containsKey(rivetWrapperId))
      throw new RuntimeException("Minder client with " + rivetWrapperId + " was not found")
    identifierMinderClientMap(rivetWrapperId)
  }

}

object TestEngine {
  def describe(clsMinderTDL: Class[MinderTdl]): util.List[Rivet] = {
    val minderTDL = clsMinderTDL.getConstructors()(0).newInstance(java.lang.Boolean.FALSE).asInstanceOf[MinderTdl]
    minderTDL.RivetDefs
  }

  /**
    * Evaluates the whole test case without running it and creates a list of wrappers and their signals-slots.
    *
    * @param tdl
    * @return
    */
  def describeTdl(tdl: Tdl): util.LinkedHashMap[String, util.Set[WrapperFunction]] = {
    val testCase = tdl.testCase;
    testCase.testAssertion = TestAssertion.findById(testCase.testAssertion.id)
    testCase.testAssertion.testGroup = TestGroup.findById(testCase.testAssertion.testGroup.id)
    val root = "_" + testCase.testAssertion.testGroup.id;
    val packagePath = root + "/_" + testCase.id;
    val minderClass = TdlCompiler.compileTdl(root, packagePath, testCase.testAssertion.testGroup.dependencyString, testCase.name, source = tdl.tdl, version = tdl.version);

    val minderTdl = try {
      minderClass.getConstructors()(0).newInstance(java.lang.Boolean.FALSE).asInstanceOf[MinderTdl]
    } catch {
      case ex: InvocationTargetException => {
        var cause: Throwable = ex
        while (cause.getCause != null) cause = cause.getCause
        throw cause
      }
    }

    val slotDefs = minderTdl.RivetDefs

    val hm: util.LinkedHashMap[String, util.Set[WrapperFunction]] = new util.LinkedHashMap()
    val map: collection.mutable.LinkedHashMap[String, util.Set[WrapperFunction]] = collection.mutable.LinkedHashMap()

    for (r <- slotDefs) {
      //check the slot
      val wrapperName = r.wrapperFunction.wrapperId
      val slotSignature = r.wrapperFunction.signature

      var set = map.getOrElseUpdate(wrapperName, new util.HashSet[WrapperFunction]())
      set.add(new WrapperFunction(wrapperId = wrapperName, signature = slotSignature))
      println(wrapperName + "::" + slotSignature)

      for (e@(k@(wn, ws), v) <- r.signalPipeMap) {
        println(wn + "::" + ws)
        set = map.getOrElseUpdate(wn, new util.HashSet[WrapperFunction]())
        set.add(new WrapperFunction(wn, ws))
      }
    }

    for ((k, v) <- map) {
      println("---------")
      println(k)
      for (ss <- v) {
        println("\t" + ss.signature)
      }
      hm.put(k, v)
    }

    hm
  }
}