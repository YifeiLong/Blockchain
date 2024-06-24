import './App.css';
import {ethers} from "ethers";
import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';

import Ballot from './artifacts/contracts/Ballot.sol/Ballot.json'

import CreateBallot from './components/CreateBallot';
import VoteBallot from './components/VoteBallot';
import JoinBallot from './components/JoinBallot';
import Winners from './components/Winners';


const ballotAddress = '0xEeFEeBc9f6eB4eFB513384edb862381d0D663368';

function App() {
    const [pollContract, setPollContract] = useState(null);
    const [createdPollId, setCreatedPollId] = useState(null);

    useEffect(() => {
        const loadBlockchainData = async () => {
            if (window.ethereum) {
                try {
                    await window.ethereum.request({ method: 'eth_requestAccounts' });
                    const provider = new ethers.providers.Web3Provider(window.ethereum);
                    const signer = provider.getSigner();
                    const contract = new ethers.Contract(ballotAddress, Ballot.abi, signer);
                    setPollContract(contract);
                } catch (error) {
                    console.error("Error connecting to MetaMask:", error);
                }
            } else {
                console.log("Please install MetaMask!");
            }
        };
        loadBlockchainData();
    }, []);

    const handlePollCreated = (pollId) => {
        setCreatedPollId(pollId);
    };

    return (
        <Router>
            <div className="App">
                <header className="App-header">
                    <nav>
                        <Link to="/create">Create Ballot</Link>
                        <Link to="/vote">Vote in Ballot</Link>
                        <Link to="/join">Join Ballot</Link>
                        <Link to="/win">Check Winners</Link>
                    </nav>
                </header>
                <div className="App-content">
                    <h1>Voting Application</h1>
                </div>
                <Routes>
                    <Route path="/create" element={<CreateBallot pollContract={pollContract} onPollCreated={handlePollCreated} />} />
                    <Route path="/vote" element={<VoteBallot pollContract={pollContract} />} />
                    <Route path="/join" element={<JoinBallot pollContract={pollContract} />} />
                    <Route path="/win" element={<Winners pollContract={pollContract} />} />
                </Routes>
            </div>
        </Router>
    );
}

export default App;
