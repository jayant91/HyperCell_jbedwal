package HyperCell

import Chisel._
import HyperCellParams.fabInConfig._

class fabInSeqDP extends Module{
	val io 		= new Bundle{
		val inConfig		= UInt(INPUT, width = datawidth)
		val inValid		= Bool(INPUT)
		
		val seqMemAddr		= UInt(INPUT, width = seqMemAddrWidth)
		val seqMemAddrValid	= Bool(INPUT)
		
		val fabInData		= Vec.fill(fabPortCount){UInt(OUTPUT, width = datawidth)}
		val fabInValid		= Vec.fill(fabPortCount){Bool(OUTPUT)}
		val fabInRdy		= Vec.fill(fabPortCount){Bool(INPUT)}
		
		val loadStore		= Vec.fill(memBankCount){UInt(INPUT, width = localStorageWidth)}
		val loadStoreValid	= Vec.fill(memBankCount){Bool(INPUT)}
		val loadStoreRdy	= Vec.fill(memBankCount){Bool(OUTPUT)}
		
		val fabStore		= Vec.fill(memBankCount){UInt(INPUT, width = localStorageWidth)}
		val fabStoreValid	= Vec.fill(memBankCount){Bool(INPUT)}
		val fabStoreRdy		= Vec.fill(memBankCount){Bool(OUTPUT)}
		
		val seqProceed		= Bool(OUTPUT)
	}

	val fabInSeqMem		= Module(new customReg(seqMemDepth, seqMemWidth))
	
	val localStorage	= Module(new controllerLocalStorage (2, localStorageDepth, localStorageWidth, memBankCount))
	
	localStorage.io.inDataFabric		:= io.fabStore
	localStorage.io.enqValidFabric		:= io.fabStoreValid
	io.fabStoreRdy				:= localStorage.io.enqRdyFabric
	
	localStorage.io.inDataLoad		:= io.loadStore
	localStorage.io.enqValidLoad		:= io.loadStoreValid
	io.loadStoreRdy				:= localStorage.io.enqRdyLoad
	
	val fabInSeqMemConfig	= Module(new memConfig(12, seqMemAddrWidth, seqMemWidth))
	
	val inPortFifo		= Vec.fill(fabPortCount){Module(new fifo(datawidth, 4)).io}
	for(i<-0 until fabPortCount){
		io.fabInData(i)				:= inPortFifo(i).deqData
		io.fabInValid(i)			:= inPortFifo(i).deqValid
		inPortFifo(i).deqRdy			:= io.fabInRdy(i)
	}
	
	
	val collectFifo		= Vec.fill(fabPortCount){Module(new fifo(datawidth, 2)).io}
	
	val bankFifo		= Vec.fill(memBankCount){Module(new fifo(datawidth, 4)).io}
	
	//fabInSeqMem and fabInSeqMemConfig connections
	fabInSeqMemConfig.io.inConfig		:= io.inConfig
	fabInSeqMemConfig.io.inValid		:= io.inValid
	fabInSeqMem.io.inData			:= fabInSeqMemConfig.io.memData
	fabInSeqMem.io.writeAddr		:= fabInSeqMemConfig.io.memAddr
	fabInSeqMem.io.writeEn			:= fabInSeqMemConfig.io.memOutValid
	
	val nextSeqSelReg		= Reg(init = Bool(false))
	val firstSeqSelReg		= Reg(init = Bool(false))
	val nextSeqReg1			= Reg(init = UInt(0, width = fabInpSeqMemWidth))
	val nextSeqReg2			= Reg(init = UInt(0, width = fabInpSeqMemWidth))
	val nextSeqRegValid1		= Reg(init = Bool(false))
	val nextSeqRegValid2		= Reg(init = Bool(false))
	val bankReadDoneReg		= Vec.fill(memBankCount){Reg(init = Bool(false))}
	val bankSeqReg			= Vec.fill(memBankCount){Reg(init = UInt(0, width = fabInpSeqMemWidth-1))}
	val seqLevelDoneReg1		= Reg(init = Bool(false))
	val seqLevelDoneReg2		= Reg(init = Bool(false))
	
	val nextSeqWire			= UInt(width = fabInpSeqMemWidth)
	val bankReadDone		= Vec.fill(memBankCount){Bool()}
	val readDone			= Vec.fill(memBankCount){Bool()}
	val allDone			= Bool()
	val collectRdy			= Bool()
	val bankToPort			= Vec.fill(memBankCount){Vec.fill(fabPortCount){UInt(width = datawidth)}}
	val bankToPortValid		= Vec.fill(memBankCount){Vec.fill(fabPortCount){Bool()}}
	
	nextSeqWire			:= UInt(0)
	allDone				:= Bool(false)
	collectRdy			:= Bool(false)
	for(i<-0 until memBankCount){
		bankReadDone(i)			:= Bool(false)
		readDone(i)			:= Bool(false)
		bankToPort(i)			:= UInt(0)
		bankToPortValid(i)		:= Bool(false)
	}
	
	
	when(nextSeqSelReg || (~firstSeqSelReg)){
		nextSeqReg1		:= nextSeqWire			
		nextSeqRegValid1	:= Bool(true)
		firstSeqSelReg		:= Bool(true)
	}
	
	when((~nextSeqSelReg) && firstSeqSelReg){
		nextSeqReg2		:= nextSeqWire
		nextSeqRegValid2	:= Bool(true)
	}
	
	
	//=====================================================================================================================================================
	val nextSeq		= UInt(width = fabInpSeqMemWidth)
	val bankSeq		= UInt(width = srcToDestWidth)
	val readAddr		= UInt(width = regIndexWidth)
	
	val readValid			= Bool()
	val readValuelocStrg		= UInt(width = datawidth)
	val isReadValid			= Bool()
	
	for(bankNo <-0 until memBankCount){
		when((nextSeqRegValid1 || nextSeqRegValid2) && bankReadDoneReg(bankNo)){
		
			when(nextSeqSelReg){
				nextSeq			:= nextSeqReg2
			}
			.otherwise{
				nextSeq			:= nextSeqReg2
			}
		
			bankSeq			:= nextSeq(srcToDestWidth-1, 0)
			readAddr		:= bankSeq(regIndexSize-1, portIdWidth)
		
			when(bankSeq(srcToDestValidBit)&& bankFifo(bankNo).enqRdy){	
				
				
				localStorage.io.readAddr(bankNo)	:= readAddr
				localStorage.io.readEn(bankNo)		:= Bool(true)
				when(bankSeq(inValidateBit)){
					localStorage.io.doInvalidate(bankNo)	:= Bool(true)
				}
				.otherwise{
					localStorage.io.doInvalidate(bankNo)	:= Bool(false)
				}
				
				isReadValid			:= localStorage.io.isReadValid(bankNo)
				
				readValuelocStrg		:= localStorage.io.outData(bankNo)
				
				bankFifo(bankNo).enqData		:= readValuelocStrg
				bankFifo(bankNo).enqValid		:= isReadValid
				
				
					
				when(isReadValid){	
					bankReadDone(bankNo)		:= Bool(true)
					seqLevelDoneReg1		:= ~(nextSeq(fabInpSeqMemWidth-1))
				}
				.otherwise{
					bankReadDone(bankNo)			:= Bool(false)
				}
				
			}
			.otherwise{
				bankReadDone(bankNo)		:= Bool(true)
			}
		
		}
		.otherwise{
			nextSeq					:= UInt(0)
			bankSeq					:= UInt(0)
			readAddr				:= UInt(0)
			
			localStorage.io.readAddr(bankNo)	:= UInt(0)
			localStorage.io.readEn(bankNo)		:= Bool(false)
			localStorage.io.doInvalidate(bankNo)	:= Bool(false)
			
			isReadValid				:= Bool(false)
			readValuelocStrg			:= localStorage.io.outData(bankNo)
			
			bankFifo(bankNo).enqData		:= readValuelocStrg
			bankFifo(bankNo).enqValid		:= Bool(false)
				
			bankReadDone(bankNo)			:= Bool(false)
		}
	}
	
	//=====================================================================================================================================================
	//Check whether any bank has read the data for current sequence. If yes
	//set corresponding bit in bank_read_done_reg. If all banks have read,
	//reset bank_read_done_reg and invalidate corresponding next_seq_vld_reg1/2.
	//and flip next_seq_sel_reg
	
	
	readDone		:= bankReadDoneReg
	for(i<-0 until memBankCount){
		when(bankReadDone(i)){
			readDone(i)		:= Bool(true)
		}
		.otherwise{
			readDone(i)		:= bankReadDoneReg(i)
		}	
	}
	
	allDone		:= bankReadDone.fold(Bool(true))(_&&_)
	
	when(allDone){
		for(i<-0 until memBankCount){
			readDone(i) 		:= Bool(false)
		}
		
		when(nextSeqSelReg){
			nextSeqRegValid2		:= Bool(false)
		}
		.otherwise{
			nextSeqRegValid2		:= Bool(false)
		}
	}
	
	bankReadDoneReg		:= readDone	

	when(allDone){
		nextSeqSelReg		:= ~(nextSeqSelReg)
	}
	
	//======================================================================================================================================================
	
	//For each bank deq from the bank fifo and write to corresponding RWire.
	//This can proceed only if either this is not the last word of the sequence
	//or all the collect fifos will be dequeued(collect_ready) which ensures
	//ordering between levels 
	val bankSeq2			= UInt(width = srcToDestWidth)
	val readValueBankFifo		= UInt(width = datawidth)
	val portId			= UInt(width = portIdWidth)
	
	for(bankNo <-0 until memBankCount){
		when((~seqLevelDoneReg2) || collectRdy){
			bankSeq2			:= bankSeqReg(bankNo)
			portId				:= bankSeq2(portIdWidth-1, 0)
			readValueBankFifo		:= bankFifo(bankNo).deqData
			
			bankFifo(bankNo).deqRdy		:= Bool(true)
			bankToPort(bankNo)(portId)	:= readValueBankFifo
			bankToPortValid(bankNo)(portId)	:= bankFifo(bankNo).deqValid
		}
		.otherwise{
			bankSeq2			:= bankSeqReg(bankNo)
			portId				:= bankSeq2(portIdWidth-1, 0)
			readValueBankFifo		:= bankFifo(bankNo).deqData
			
			bankFifo(bankNo).deqRdy		:= Bool(false)
			bankToPort(bankNo)(portId)	:= readValueBankFifo
			bankToPortValid(bankNo)(portId)	:= Bool(false)
		}
	}
	
	//=====================================================================================================================================================
	
	
	for(portNo <-0 until fabPortCount){
		//For each port if any bank has set an RWire corresponding to this port, read it and
		//enq to the collect fifo.
		val collectEnq		= Bool()
	
		val bankVal		= Vec.fill(memBankCount){UInt(width = datawidth)}
		val bankValValid	= Vec.fill(memBankCount){Bool()}
		val portVal		= UInt(width = datawidth)
		
		collectEnq		:= Bool(false)
		portVal			:= UInt(0)
		
		for(bankNo<-0 until memBankCount){
			bankVal(bankNo)			:= bankToPort(bankNo)(portNo)
			bankValValid(bankNo)		:= bankToPortValid(bankNo)(portNo)
		}
		
		collectEnq	:= bankValValid.fold(Bool(true))(_&&_)
		
		when(collectEnq){
			portVal			:= bankVal.fold(Bool(true))(_|_)
			seqLevelDoneReg2	:= seqLevelDoneReg1
		}
		.otherwise{
			portVal			:= UInt(0)
		}
	
		when(collectEnq && collectFifo(portNo).enqRdy){
			collectFifo(portNo).enqData	:= portVal 
			collectFifo(portNo).enqValid	:= Bool(true)
		}
		.otherwise{
			collectFifo(portNo).enqData	:= portVal 
			collectFifo(portNo).enqValid	:= Bool(false)
		}
	}

	//=====================================================================================================================================================
	
	//If last word chck for all collect fifos free 
	val rdyInit		= Bool()
	val rdy			= Bool()
	val enqRdyCollect	= Vec.fill(fabPortCount){Bool()}
	when(seqLevelDoneReg2){
		for(i<-0 until fabPortCount){
			enqRdyCollect(i)	:= collectFifo(i).enqRdy
		}
		
		rdyInit		:= Bool(true)
		rdy		:= rdyInit && enqRdyCollect.fold(Bool(true))(_&&_)
		
		when(rdy){
			collectRdy		:= Bool(true)
		}
		.otherwise{
			collectRdy		:= Bool(false)
		}
	}
	.otherwise{
		rdyInit		:= Bool(false)
		collectRdy	:= Bool(false)
		rdy		:= Bool(false)
		for(i<-0 until fabPortCount){
			enqRdyCollect(i)	:= collectFifo(i).enqRdy
		}
	}
	
	//=====================================================================================================================================================
	
	//For every port, if data for all ports are ready(last word) and the port fifo is free,
	//deq from collect_fifo and enq to inp_port_fifo
	
	for(portNo <-0 until fabPortCount){
		when(seqLevelDoneReg2){
			inPortFifo(portNo).enqData	:= collectFifo(portNo).deqData
			inPortFifo(portNo).enqValid	:= collectFifo(portNo).deqValid
			collectFifo(portNo).deqRdy	:= inPortFifo(portNo).enqRdy
		}
		.otherwise{
			inPortFifo(portNo).enqData	:= collectFifo(portNo).deqData
			inPortFifo(portNo).enqValid	:= Bool(false)
			collectFifo(portNo).deqRdy	:= Bool(false)
		}
	}
	
	
	//====================================================================================================================================================
	
	when(fabInSeqMemConfig.io.rst){
		for(portNo <-0 until fabPortCount){
			collectFifo(portNo).rst		:= Bool(true)
			inPortFifo(portNo).rst		:= Bool(true)
		}
		
		for(bankNo <-0 until memBankCount){
			bankFifo(bankNo).rst		:= Bool(true)
			bankReadDoneReg(bankNo)		:= Bool(false)
		}
		
		nextSeqRegValid1		:= Bool(false)
		nextSeqRegValid2		:= Bool(false)
		nextSeqSelReg			:= Bool(false)
		firstSeqSelReg			:= Bool(false)
		seqLevelDoneReg1		:= Bool(false)
		seqLevelDoneReg2		:= Bool(false)
		
		localStorage.io.rst		:= Bool(true)
			
	}
	.otherwise{
		for(portNo <-0 until fabPortCount){
			collectFifo(portNo).rst		:= Bool(false)
			inPortFifo(portNo).rst		:= Bool(false)
		}
		
		for(bankNo <-0 until memBankCount){
			bankFifo(bankNo).rst		:= Bool(false)
		}
		
		localStorage.io.rst		:= Bool(false)
	}
		
	//====================================================================================================================================================
	
	when((~nextSeqRegValid1) || (~nextSeqRegValid2)){
		io.seqProceed	:= Bool(true)
	}
	.otherwise{
		io.seqProceed	:= Bool(false)
	}
	
	when(io.seqMemAddrValid){
		fabInSeqMem.io.readEn	:= io.seqMemAddrValid
		fabInSeqMem.io.readAddr	:= io.seqMemAddr
		nextSeqWire		:= fabInSeqMem.io.outData
	}
	.otherwise{
		fabInSeqMem.io.readEn	:= io.seqMemAddrValid
		fabInSeqMem.io.readAddr	:= io.seqMemAddr
		nextSeqWire		:= UInt(0)
	}
	
	//====================================================================================================================================================
	
	
	
	
	
	
}
