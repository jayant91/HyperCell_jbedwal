package HyperCell

import Chisel._
import HyperCellParams.MemConfig._

class memConfig(memType: Int, memAddrWidth : Int, memDataWidth : Int) extends Module{

	val iterCntSize	:Int	= (java.lang.Math.floor(memDataWidth/(dataWidth-1))).intValue() + 1
	
	val io		= new Bundle{
		val inConfig		= UInt(INPUT, width = dataWidth) 	
		val inValid		= Bool(INPUT)
		
		val memAddr		= UInt(OUTPUT, width = memAddrWidth)
		val memData		= UInt(OUTPUT, width = memDataWidth)
		val memOutValid		= Bool(OUTPUT)
		
		val rst			= Bool(OUTPUT)
		
	}
	
	val inConfigReg		= Reg(init = UInt(0, width = dataWidth))
	val memTypeMatch	= Reg(init = Bool(false))
	val memAddr		= Reg(init = UInt(0, width = memAddrWidth))
	val memData		= Vec.fill(iterCntSize){Reg(init = UInt(0, width = (dataWidth-1)))}
	val iterCnt		= Reg(init = UInt(0))
	val memOutValid		= Reg(init = Bool(false))
	
	val startCompute	= Bool()
	startCompute		:= Bool(false)
	
	when(io.inValid){
		inConfigReg		:= io.inConfig
	}
	
	when((inConfigReg(dataWidth-1) === UInt(0)) && (inConfigReg(dataWidth-2, dataWidth -3) === UInt(0)) && (inConfigReg(0) === UInt(1))){
		startCompute	:= Bool(true)
	}
	.elsewhen(inConfigReg(dataWidth-1, combMemWidth) === UInt(256)){
		
		when(inConfigReg(memTypeWidth -1, memTypeWidth - vMemWidth) === UInt(memType)){
		//Memeory Type Matched
			memTypeMatch		:= Bool(true)
		}
		.otherwise{
			memTypeMatch		:= Bool(false)
		}
		
		startCompute		:= Bool(false)
	}
	.elsewhen(inConfigReg(dataWidth-1, combMemWidth) === UInt(259)){
		
		when(inConfigReg(memTypeWidth -1, memTypeWidth - vMemWidth) === UInt(memType)){
		//Memeory Type Matched
			memTypeMatch		:= Bool(true)
		}
		.otherwise{
			memTypeMatch		:= Bool(false)
		}
		
		startCompute		:= Bool(false)
	}
	.elsewhen(inConfigReg(dataWidth-1, combMemWidth) === UInt(257)){
		
		when(inConfigReg(memTypeWidth -1, memTypeWidth - vMemWidth) === UInt(memType)){
		//Memeory Type Matched
			memTypeMatch		:= Bool(true)
		}
		.otherwise{
			memTypeMatch		:= Bool(false)
		}
		
		startCompute		:= Bool(false)
	}
	.elsewhen(inConfigReg(dataWidth-1, combMemWidth) === UInt(256)){
		
		when(inConfigReg(memTypeWidth -1, memTypeWidth - vMemWidth) === UInt(memType)){
		//Memeory Type Matched
			memTypeMatch		:= Bool(true)
		}
		.otherwise{
			memTypeMatch		:= Bool(false)
		}
		
		startCompute		:= Bool(false)
	}

	when(memTypeMatch && (inConfigReg(dataWidth-1)=== UInt(1))){
		memData(iterCnt)	:= inConfigReg(dataWidth-2, 0)
		when(iterCnt < UInt(iterCntSize-1)){
			iterCnt			:= iterCnt + UInt(1)
			
		}
		.elsewhen(iterCnt === UInt(iterCntSize-1)){
			iterCnt			:= UInt(0)
			memOutValid		:= Bool(true)
		}
		
	}

	
	
	when(memOutValid){
		io.memData		:= Cat((0 until (iterCntSize)).map(i=> {memData(iterCntSize-i-1)}))
		io.memAddr		:= memAddr
		io.memOutValid		:= Bool(true)
		memOutValid		:= Bool(false)
		memAddr			:= memAddr + UInt(1)
	}
	.otherwise{
		io.memData		:= UInt(0)
		io.memAddr		:= UInt(0)
		io.memOutValid		:= Bool(false)
	}
		
	when(startCompute){
		io.rst		:= Bool(true)
	}
	.otherwise{
		io.rst		:= Bool(false)
	}
}

class memConfigTest(c: memConfig) extends Tester(c){	
	val range : Int = (scala.math.pow(2, 30)).toInt
	var i0  : Int = 0x40100000
	
	poke(c.io.inConfig, i0)
	poke(c.io.inValid, 0x1)
	
	peek(c.io.memAddr)
	peek(c.io.memOutValid)
	peek(c.io.rst)
	
	step(1)
	 
	for(i<-0 until 12)
	{
		var i1  : Int = rnd.nextInt(range) + 0x80000000
		poke(c.io.inConfig, i1)
		peek(c.iterCnt)
		peek(c.memTypeMatch)
		peek(c.io.memAddr)
		peek(c.io.memData)
		peek(c.io.memOutValid)
		peek(c.memAddr)
		peek(c.inConfigReg)
		peek(c.memTypeMatch)
		peek(c.memOutValid)
		peek(c.io.memOutValid)
		peek(c.io.rst)
		step(1)
	}
	
	
}

object memConfigMain {
    def main(args: Array[String]) {
	chiselMainTest(Array[String]("--backend", "c", "--genHarness",  "--debug", "--compile", "--test"),
	() => Module(new memConfig(2, 5, 40))){c => new memConfigTest(c)}
    }
}
